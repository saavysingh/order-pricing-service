package com.example.orderservice.demo;

import java.time.Instant;
import java.util.UUID;

public record DemoRecentOrder(
        UUID orderId,
        Instant createdAt,
        String pricingStatus,
        Integer subtotalCents,
        Integer finalPriceCents
) {
}