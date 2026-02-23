package com.example.pricingservice;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OrderItemPayload(
        @JsonProperty("sku") String sku,
        @JsonProperty("qty") Integer qty,
        @JsonProperty("unit_price_cents") Integer unitPriceCents
) {
}
