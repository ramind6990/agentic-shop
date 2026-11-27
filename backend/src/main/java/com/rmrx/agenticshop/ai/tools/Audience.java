package com.rmrx.agenticshop.ai.tools;

import com.rmrx.agenticshop.app.identity.entity.Role;

import java.util.Optional;

/**
 * Who the agent works for; decides the tools offered and the instructions. Differences inside an audience (e.g. the
 * refund limit of SUPPORT_T1 vs SUPPORT_T2) are enforced by the shop's services, not by tool selection.
 */
public enum Audience {

    /** A signed-in customer: their own orders, returns and refunds. */
    CUSTOMER,

    /** Support staff (SUPPORT_T1, SUPPORT_T2, ADMIN) helping customers: any customer's data, staff actions. */
    STAFF;

    /** Roles without agent chat (seller, finance, fraud) get none. */
    public static Optional<Audience> of(Role role) {
        if (role == null) return Optional.empty();
        return switch (role) {
            case CUSTOMER -> Optional.of(CUSTOMER);
            case SUPPORT_T1, SUPPORT_T2, ADMIN -> Optional.of(STAFF);
            default -> Optional.empty();
        };
    }
}
