package com.example.pricingservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class PricingConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(PricingConsumer.class);

    private static final String TOPIC_ORDER_CREATED = "order.created";
    private static final String TOPIC_ORDER_CREATED_RETRY = "order.created.retry";
    private static final String TOPIC_ORDER_CREATED_DLQ = "order.created.dlq";
    private static final int MAX_ATTEMPTS = 5;
    private static final int[] RETRY_BACKOFF_SECONDS = {1, 2, 3, 4, 5};

    private final ObjectMapper objectMapper;
    private final PricingResultRepository pricingResultRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final TransactionTemplate transactionTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public PricingConsumer(ObjectMapper objectMapper, PricingResultRepository pricingResultRepository, ProcessedEventRepository processedEventRepository, TransactionTemplate transactionTemplate, JdbcTemplate jdbcTemplate, KafkaTemplate<String, String> kafkaTemplate) {
        this.objectMapper = objectMapper;
        this.pricingResultRepository = pricingResultRepository;
        this.processedEventRepository = processedEventRepository;
        this.transactionTemplate = transactionTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = TOPIC_ORDER_CREATED, groupId = "pricing-service")
    public void onMessage(String message, Acknowledgment acknowledgment) {
        handleMessage(message, acknowledgment);
    }

    @KafkaListener(topics = TOPIC_ORDER_CREATED_RETRY, groupId = "pricing-service")
    public void onRetryMessage(String message, Acknowledgment acknowledgment) {
        handleMessage(message, acknowledgment);
    }

    private void handleMessage(String message, Acknowledgment acknowledgment) {
        try {
            ProcessingResult result = transactionTemplate.execute(status -> processMessage(message, status));
            if (result == ProcessingResult.SUCCESS || result == ProcessingResult.DUPLICATE) {
                acknowledgment.acknowledge();
            }
        } catch (Exception ex) {
            handleFailure(message, ex);
            acknowledgment.acknowledge();
        }
    }

    private ProcessingResult processMessage(String message, org.springframework.transaction.TransactionStatus status) {
        OrderCreatedPayload payload = parse(message);

        if (shouldSimulateFailure(payload)) {
            LOGGER.warn("Simulated failure for orderId={}, eventId={}, attempt={}", payload.orderId(), payload.eventId(), payload.attempt());
            throw new IllegalStateException("Simulated pricing failure");
        }

        int taxCents = taxCents(payload.subtotalCents());
        int discountCents = discountCents(payload.promo(), payload.subtotalCents());
        int finalPriceCents = payload.subtotalCents() + taxCents - discountCents;

        PricingResult result = new PricingResult(
                payload.orderId(),
                payload.eventId(),
                finalPriceCents,
                taxCents,
                discountCents,
                Instant.now()
        );
        pricingResultRepository.save(result);

        updateOrderPricing(payload.orderId(), finalPriceCents);

        try {
            ProcessedEvent processedEvent = new ProcessedEvent(payload.eventId(), payload.orderId(), Instant.now());
            processedEventRepository.save(processedEvent);
        } catch (DataIntegrityViolationException ex) {
            status.setRollbackOnly();
            return ProcessingResult.DUPLICATE;
        }

        LOGGER.info("Pricing succeeded for orderId={}, eventId={}, attempt={}", payload.orderId(), payload.eventId(), payload.attempt());
        return ProcessingResult.SUCCESS;
    }

    private OrderCreatedPayload parse(String message) {
        try {
            return objectMapper.readValue(message, OrderCreatedPayload.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid order.created payload", ex);
        }
    }

    private void handleFailure(String message, Exception ex) {
        OrderCreatedPayload payload = tryParse(message);
        if (payload == null) {
            sendDlq(message, 1, ex);
            return;
        }

        int attempt = payload.attempt() == null ? 0 : payload.attempt();
        int nextAttempt = attempt + 1;
        if (nextAttempt > MAX_ATTEMPTS) {
            LOGGER.warn("Exhausted attempts for orderId={}, eventId={}, attempts={}", payload.orderId(), payload.eventId(), nextAttempt);
            sendDlq(payload, nextAttempt, ex);
            return;
        }

        Instant nextAttemptAt = Instant.now().plusSeconds(retryDelaySeconds(nextAttempt));
        LOGGER.info("Scheduling retry for orderId={}, eventId={}, attempt={}, nextAttemptAt={}", payload.orderId(), payload.eventId(), nextAttempt, nextAttemptAt);
        sleepUntil(nextAttemptAt);
        OrderCreatedPayload retryPayload = new OrderCreatedPayload(
                payload.eventId(),
                payload.orderId(),
                payload.items(),
                payload.currency(),
                payload.subtotalCents(),
                payload.promo(),
                nextAttempt,
                nextAttemptAt.toString()
        );

        sendRetry(retryPayload);
    }

    private boolean shouldSimulateFailure(OrderCreatedPayload payload) {
        if (payload.promo() == null) {
            return false;
        }

        if ("FAIL".equalsIgnoreCase(payload.promo())) {
            return true;
        }

        if ("RETRY2".equalsIgnoreCase(payload.promo())) {
            int attempt = payload.attempt() == null ? 0 : payload.attempt();
            return attempt < 3;
        }

        return false;
    }

    private void sleepUntil(Instant nextAttemptAt) {
        long delayMs = nextAttemptAt.toEpochMilli() - Instant.now().toEpochMilli();
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private int retryDelaySeconds(int attempt) {
        int index = Math.min(attempt - 1, RETRY_BACKOFF_SECONDS.length - 1);
        return RETRY_BACKOFF_SECONDS[index];
    }

    private void sendRetry(OrderCreatedPayload payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(TOPIC_ORDER_CREATED_RETRY, payload.orderId().toString(), json);
        } catch (Exception ex) {
            sendDlq(payload, payload.attempt(), ex);
        }
    }

    private void sendDlq(Object payload, Integer attempt, Exception ex) {
        PricingDlqMessage dlqMessage = new PricingDlqMessage(
                payload,
                attempt,
                ex.getMessage(),
                Instant.now().toString()
        );
        try {
            String json = objectMapper.writeValueAsString(dlqMessage);
            kafkaTemplate.send(TOPIC_ORDER_CREATED_DLQ, "dlq", json);
            upsertDlqEvent(payload, attempt, ex.getMessage());
        } catch (Exception ignored) {
            // best effort
        }
    }

    private void upsertDlqEvent(Object payload, Integer attempt, String error) {
        if (!(payload instanceof OrderCreatedPayload orderPayload)) {
            return;
        }
        jdbcTemplate.update(
                "insert into dlq_events (event_id, order_id, topic, attempts, error, created_at) " +
                        "values (?, ?, ?, ?, ?, now()) " +
                        "on conflict (event_id) do update set attempts = excluded.attempts, error = excluded.error, created_at = excluded.created_at",
                orderPayload.eventId(),
                orderPayload.orderId(),
                TOPIC_ORDER_CREATED_DLQ,
                attempt == null ? 0 : attempt,
                error
        );
        jdbcTemplate.update(
            "update orders set pricing_status = ? where order_id = ?",
            "FAILED",
            orderPayload.orderId()
        );
    }

    private OrderCreatedPayload tryParse(String message) {
        try {
            return objectMapper.readValue(message, OrderCreatedPayload.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private int taxCents(int subtotalCents) {
        return (subtotalCents * 8 + 50) / 100;
    }

    private int discountCents(String promo, int subtotalCents) {
        if (promo == null || promo.isBlank()) {
            return 0;
        }
        if ("PROMO10".equalsIgnoreCase(promo)) {
            return (subtotalCents * 10 + 50) / 100;
        }
        return 0;
    }

    private void updateOrderPricing(UUID orderId, int finalPriceCents) {
        jdbcTemplate.update(
                "update orders set pricing_status = ?, final_price_cents = ? where order_id = ?",
                "PRICED",
                finalPriceCents,
                orderId
        );
    }

    private enum ProcessingResult {
        SUCCESS,
        DUPLICATE
    }
}
