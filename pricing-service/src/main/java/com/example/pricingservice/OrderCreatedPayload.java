package com.example.pricingservice;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

public record OrderCreatedPayload(
        @JsonProperty("event_id") UUID eventId,
        @JsonProperty("order_id") UUID orderId,
        @JsonProperty("items") List<OrderItemPayload> items,
        @JsonProperty("currency") String currency,
        @JsonProperty("subtotal_cents") Integer subtotalCents,
        @JsonProperty("promo") String promo,
        @JsonProperty("attempt") Integer attempt,
        @JsonProperty("next_attempt_at") String nextAttemptAt
) {
}
