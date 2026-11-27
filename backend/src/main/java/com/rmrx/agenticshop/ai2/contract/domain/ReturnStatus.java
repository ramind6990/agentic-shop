package com.rmrx.agenticshop.ai2.contract.domain;

/** Where a return request stands; CANCELLED = withdrawn by the customer. */
public enum ReturnStatus {
    REQUESTED,
    APPROVED,
    REJECTED,
    RECEIVED,
    DISPUTED,
    CLOSED,
    CANCELLED
}
