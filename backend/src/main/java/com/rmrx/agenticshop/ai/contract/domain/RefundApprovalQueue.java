package com.rmrx.agenticshop.ai.contract.domain;

/** Which refund approval requests to list. */
public enum RefundApprovalQueue {
    /** Waiting requests of colleagues that your limit covers: the ones you may decide. */
    TO_DECIDE,
    /** Your own requests and their outcome. */
    REQUESTED_BY_ME,
    /** Every request. */
    ALL
}
