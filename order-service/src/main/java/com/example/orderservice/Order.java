package com.example.orderservice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "subtotal_cents", nullable = false)
    private Integer subtotalCents;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "pricing_status")
    private String pricingStatus;

    @Column(name = "final_price_cents")
    private Integer finalPriceCents;

    protected Order() {
    }

    public Order(UUID orderId, String customerId, String currency, Integer subtotalCents, String status) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.currency = currency;
        this.subtotalCents = subtotalCents;
        this.status = status;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getCurrency() {
        return currency;
    }

    public Integer getSubtotalCents() {
        return subtotalCents;
    }

    public String getStatus() {
        return status;
    }

    public String getPricingStatus() {
        return pricingStatus;
    }

    public Integer getFinalPriceCents() {
        return finalPriceCents;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setPricingStatus(String pricingStatus) {
        this.pricingStatus = pricingStatus;
    }

    public void setFinalPriceCents(Integer finalPriceCents) {
        this.finalPriceCents = finalPriceCents;
    }
}
