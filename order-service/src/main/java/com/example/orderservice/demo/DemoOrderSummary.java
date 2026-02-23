package com.example.orderservice.demo;

import java.time.Instant;
import java.util.UUID;

public record DemoOrderSummary(
        UUID orderId,
        String customerId,
        String currency,
        Integer subtotalCents,
        String status,
        Instant createdAt
) {
}