package com.rmrx.agenticshop.ai.graph.guard.precheck;

import com.rmrx.agenticshop.app.returns.service.RefundCheck;

/** Approval-detail lines about a refund's limits. */
final class RefundNotes {

    private RefundNotes() {
    }

    static String left(RefundCheck check) {
        return check.remaining() + " " + check.currency() + " can still be refunded on this order"
                + (check.cap() == null ? "." : "; your limit is " + check.cap() + " " + check.currency()
                + " per refund.");
    }
}
