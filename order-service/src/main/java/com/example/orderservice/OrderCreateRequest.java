package com.example.orderservice;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record OrderCreateRequest(
        @JsonProperty("customer_id") String customerId,
        @JsonProperty("currency") String currency,
        @JsonProperty("items") List<OrderItemRequest> items,
        @JsonProperty("promo_code") String promoCode
) {
}
