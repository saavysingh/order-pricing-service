package com.example.orderservice;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OrderItemRequest(
        @JsonProperty("sku") String sku,
        @JsonProperty("qty") Integer qty,
        @JsonProperty("unit_price_cents") Integer unitPriceCents
) {
}
