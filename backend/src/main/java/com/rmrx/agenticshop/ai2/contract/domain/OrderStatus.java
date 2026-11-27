package com.rmrx.agenticshop.ai2.contract.domain;

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
