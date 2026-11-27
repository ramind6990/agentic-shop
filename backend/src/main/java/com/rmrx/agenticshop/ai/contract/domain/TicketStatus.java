package com.rmrx.agenticshop.ai.contract.domain;

/** Where a support ticket stands: OPEN (waiting on support), PENDING (waiting on the customer), ESCALATED (with T2), RESOLVED, CLOSED. */
public enum TicketStatus {
    OPEN,
    PENDING,
    ESCALATED,
    RESOLVED,
    CLOSED
}
