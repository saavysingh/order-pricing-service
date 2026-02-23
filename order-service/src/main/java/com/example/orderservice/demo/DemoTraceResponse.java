package com.example.orderservice.demo;

import java.time.Instant;
import java.util.UUID;

public record DemoTraceResponse(
        UUID orderId,
        UUID eventId,
        boolean orderCreated,
        String orderStatus,
        String pricingStatus,
        Integer subtotalCents,
        Integer finalPriceCents,
        OutboxTrace outbox,
        PricingTrace pricing,
        DlqTrace dlq
) {

    public record OutboxTrace(
            String status,
            Integer attempts,
            Instant createdAt,
            Instant publishedAt,
            Instant nextAttemptAt,
            String lastError
    ) {
    }

    public record PricingTrace(
            boolean hasPricingResult,
            Instant computedAt,
            Integer taxCents,
            Integer discountCents,
            boolean processedEvent,
            Instant processedAt
    ) {
    }

    public record DlqTrace(
            String state,
            Integer attempts,
            String lastError,
            Instant lastUpdatedAt
    ) {
    }
}