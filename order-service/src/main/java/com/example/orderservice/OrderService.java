package com.example.orderservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OrderService(OrderRepository orderRepository, OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Order createOrder(OrderCreateRequest request) {
        int subtotal = request.items().stream()
                .mapToInt(item -> item.qty() * item.unitPriceCents())
                .sum();

        Order order = new Order(
                UUID.randomUUID(),
                request.customerId(),
                request.currency(),
                subtotal,
                "CREATED"
        );

        Order saved = orderRepository.save(order);

        UUID eventId = UUID.randomUUID();
        String outboxPayload = buildOutboxPayload(eventId, saved.getOrderId(), request, subtotal);

        OutboxEvent outboxEvent = new OutboxEvent(
                eventId,
                "ORDER_CREATED",
                "ORDER",
                saved.getOrderId(),
                outboxPayload,
                "NEW",
                0,
                Instant.now(),
                Instant.now(),
                null,
                null
        );
        outboxEventRepository.save(outboxEvent);

        return saved;
    }

    public Order getOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    private String buildOutboxPayload(UUID eventId, UUID orderId, OrderCreateRequest request, int subtotal) {
        try {
            OrderCreatedPayload payload = new OrderCreatedPayload(
                    eventId,
                    orderId,
                    request.items(),
                    request.currency(),
                    subtotal,
                    request.promoCode(),
                    0,
                    null
            );
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to build outbox payload", ex);
        }
    }
}
