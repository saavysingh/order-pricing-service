package com.example.pricingservice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pricing_results")
public class PricingResult {

    @Id
    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "final_price_cents", nullable = false)
    private Integer finalPriceCents;

    @Column(name = "tax_cents", nullable = false)
    private Integer taxCents;

    @Column(name = "discount_cents", nullable = false)
    private Integer discountCents;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    protected PricingResult() {
    }

    public PricingResult(UUID orderId, UUID eventId, Integer finalPriceCents, Integer taxCents, Integer discountCents, Instant computedAt) {
        this.orderId = orderId;
        this.eventId = eventId;
        this.finalPriceCents = finalPriceCents;
        this.taxCents = taxCents;
        this.discountCents = discountCents;
        this.computedAt = computedAt;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getEventId() {
        return eventId;
    }

    public Integer getFinalPriceCents() {
        return finalPriceCents;
    }

    public Integer getTaxCents() {
        return taxCents;
    }

    public Integer getDiscountCents() {
        return discountCents;
    }

    public Instant getComputedAt() {
        return computedAt;
    }
}
