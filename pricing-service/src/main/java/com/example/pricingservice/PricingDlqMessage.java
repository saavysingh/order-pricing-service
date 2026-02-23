package com.example.pricingservice;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PricingDlqMessage(
        @JsonProperty("payload") Object payload,
        @JsonProperty("attempt") Integer attempt,
        @JsonProperty("error") String error,
        @JsonProperty("timestamp") String timestamp
) {
}
