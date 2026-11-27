package com.rmrx.agenticshop.app.returns.entity;

/** Where a refund approval request stands. */
public enum RefundApprovalStatus {
    /** Waiting for a colleague whose refund limit covers the amount. */
    PENDING,
    /** Approved: the refund was issued. */
    APPROVED,
    /** Rejected: nothing was refunded. */
    REJECTED
}
