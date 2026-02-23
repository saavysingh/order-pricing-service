package com.example.orderservice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/orders")
public class OrderController {

    private final OrderService orderService;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ObjectMapper objectMapper;
    private final OutboxEventRepository outboxEventRepository;

    public OrderController(OrderService orderService, IdempotencyRecordRepository idempotencyRecordRepository, ObjectMapper objectMapper, OutboxEventRepository outboxEventRepository) {
        this.orderService = orderService;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.objectMapper = objectMapper;
        this.outboxEventRepository = outboxEventRepository;
    }

    @PostMapping
    public ResponseEntity<String> createOrder(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody OrderCreateRequest request
    ) throws JsonProcessingException {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":\"Missing Idempotency-Key\"}");
        }

        synchronized (idempotencyKey.intern()) {
            String canonical = canonicalString(request);
            String requestHash = sha256Hex(canonical.getBytes());

            IdempotencyRecord existing = idempotencyRecordRepository.findById(idempotencyKey).orElse(null);
            if (existing != null) {
                if (!existing.getRequestHash().equals(requestHash)) {
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"Idempotency-Key reuse with different payload\"}");
                }
                if (existing.getOrderId() == null) {
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"Idempotency-Key in progress\"}");
                }
                return ResponseEntity.status(existing.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(existing.getResponseBody());
            }

            try {
                IdempotencyRecord inProgress = new IdempotencyRecord(
                    idempotencyKey,
                    requestHash,
                    null,
                    HttpStatus.ACCEPTED.value(),
                    "{}",
                    Instant.now()
                );
                idempotencyRecordRepository.saveAndFlush(inProgress);
            } catch (DataIntegrityViolationException ex) {
                IdempotencyRecord concurrent = idempotencyRecordRepository.findById(idempotencyKey).orElse(null);
                if (concurrent != null) {
                    if (!concurrent.getRequestHash().equals(requestHash)) {
                        return ResponseEntity.status(HttpStatus.CONFLICT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("{\"error\":\"Idempotency-Key reuse with different payload\"}");
                    }
                    if (concurrent.getOrderId() == null) {
                        return ResponseEntity.status(HttpStatus.CONFLICT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("{\"error\":\"Idempotency-Key in progress\"}");
                    }
                    return ResponseEntity.status(concurrent.getStatusCode())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(concurrent.getResponseBody());
                }
                throw ex;
            }

            Order order = orderService.createOrder(request);
            OrderResponse response = toResponse(order, findEventId(order.getOrderId()));
            String responseBody = objectMapper.writeValueAsString(response);

            IdempotencyRecord record = new IdempotencyRecord(
                idempotencyKey,
                requestHash,
                order.getOrderId(),
                HttpStatus.OK.value(),
                responseBody,
                Instant.now()
            );
            idempotencyRecordRepository.save(record);

            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(responseBody);
        }
    }

    @GetMapping("/{orderId}")
    public OrderResponse getOrder(@PathVariable UUID orderId) {
        Order order = orderService.getOrder(orderId);
        return toResponse(order, findEventId(orderId));
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<String> handleNotFound(OrderNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    private OrderResponse toResponse(Order order, UUID eventId) {
        return new OrderResponse(
                order.getOrderId(),
                eventId,
                order.getStatus(),
                order.getSubtotalCents(),
                order.getCurrency(),
                order.getCustomerId()
        );
    }

    private UUID findEventId(UUID orderId) {
        return outboxEventRepository
                .findTopByAggregateIdAndEventTypeOrderByCreatedAtDesc(orderId, "ORDER_CREATED")
                .map(OutboxEvent::getEventId)
                .orElse(null);
    }

    private String sha256Hex(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String canonicalString(OrderCreateRequest request) {
        //customer_id|CURRENCY|SKU1:2:1500,SKU2:1:2200
        String customerId = request.customerId() == null ? "" : request.customerId().trim();
        String currency = request.currency() == null ? "" : request.currency().trim().toUpperCase();
        String promo = request.promoCode() == null ? "" : request.promoCode().trim();
        String items = request.items().stream()
                .sorted((a, b) -> {
                    int skuCompare = nullSafe(a.sku()).compareTo(nullSafe(b.sku()));
                    if (skuCompare != 0) {
                        return skuCompare;
                    }
                    int priceCompare = Integer.compare(nullSafeInt(a.unitPriceCents()), nullSafeInt(b.unitPriceCents()));
                    if (priceCompare != 0) {
                        return priceCompare;
                    }
                    return Integer.compare(nullSafeInt(a.qty()), nullSafeInt(b.qty()));
                })
                .map(item -> String.format("%s:%d:%d",
                        nullSafe(item.sku()),
                        nullSafeInt(item.qty()),
                        nullSafeInt(item.unitPriceCents())
                ))
                .reduce((a, b) -> a + "," + b)
                .orElse("");

        return customerId + "|" + currency + "|" + promo + "|" + items;
    }

    private String nullSafe(String value) {
        return value == null ? "" : value.trim();
    }

    private int nullSafeInt(Integer value) {
        return value == null ? 0 : value;
    }
}
