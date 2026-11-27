package com.rmrx.agenticshop.app.returns.entity;

public enum ReturnStatus {
    REQUESTED,
    APPROVED,
    REJECTED,
    RECEIVED,
    DISPUTED,
    CLOSED,
    /** Withdrawn by the customer before the seller acted on it. */
    CANCELLED
}
