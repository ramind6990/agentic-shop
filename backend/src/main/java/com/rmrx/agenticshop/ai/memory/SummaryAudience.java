package com.rmrx.agenticshop.ai.memory;

import com.rmrx.agenticshop.ai.context.AgentContext;

/**
 * Whose view of the timeline a summary covers. CUSTOMER: only customer-visible messages (internal = false), used for
 * customer turns. STAFF: every message including internal notes, used for staff turns.
 */
public enum SummaryAudience {
    CUSTOMER,
    STAFF;

    public static SummaryAudience of(AgentContext ctx) {
        return ctx.isCustomer() ? CUSTOMER : STAFF;
    }

    /** Whether this audience sees only customer-visible messages. */
    public boolean customerVisibleOnly() {
        return this == CUSTOMER;
    }
}
