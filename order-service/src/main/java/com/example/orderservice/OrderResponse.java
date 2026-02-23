package com.example.orderservice;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record OrderResponse(
        @JsonProperty("order_id") UUID orderId,
        @JsonProperty("event_id") UUID eventId,
        @JsonProperty("status") String status,
        @JsonProperty("subtotal_cents") Integer subtotalCents,
        @JsonProperty("currency") String currency,
        @JsonProperty("customer_id") String customerId
) {
}
