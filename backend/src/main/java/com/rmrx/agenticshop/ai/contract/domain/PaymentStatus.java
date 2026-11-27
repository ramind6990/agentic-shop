package com.rmrx.agenticshop.ai.contract.domain;

/** Where a payment stands. */
public enum PaymentStatus {
    AUTHORIZED,
    CAPTURED,
    FAILED,
    REFUNDED,
    PARTIALLY_REFUNDED,
    VOIDED
}
