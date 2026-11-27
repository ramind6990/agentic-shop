package com.rmrx.agenticshop.ai.contract.domain;

/** Where an order stands. */
public enum OrderStatus {
    PENDING,
    PAID,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    RETURNED,
    REFUNDED,
    PARTIALLY_REFUNDED
}
