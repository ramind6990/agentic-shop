package com.rmrx.agenticshop.app.returns.service;

/** Which refund approval requests a staff member looks at. */
public enum RefundApprovalQueue {
    /** Waiting requests of colleagues that the caller's limit covers. */
    TO_DECIDE,
    /** The caller's own requests, any status. */
    REQUESTED_BY_ME,
    /** Every request. */
    ALL
}
