package com.rmrx.agenticshop.app.returns.service;

import com.rmrx.agenticshop.app.identity.entity.Role;

import java.math.BigDecimal;
import java.util.Optional;

/** The largest refund each role may issue on its own; empty = no limit. Roles not listed cannot issue refunds. */
public final class RefundLimits {

    private RefundLimits() {
    }

    public static boolean mayIssue(Role role) {
        return switch (role) {
            case SUPPORT_T1, SUPPORT_T2, FINANCE, ADMIN -> true;
            default -> false;
        };
    }

    public static Optional<BigDecimal> capFor(Role role) {
        return switch (role) {
            case SUPPORT_T1 -> Optional.of(new BigDecimal("100.00"));
            case SUPPORT_T2 -> Optional.of(new BigDecimal("1000.00"));
            default -> Optional.empty();
        };
    }

    /** Whether the role may issue (or approve) a refund of this amount on its own. */
    public static boolean covers(Role role, BigDecimal amount) {
        return mayIssue(role) && capFor(role).map(cap -> amount.compareTo(cap) <= 0).orElse(true);
    }

    /** The lowest role whose limit covers the amount: who has to approve a refund above the requester's limit. */
    public static Role approverFor(BigDecimal amount) {
        return covers(Role.SUPPORT_T2, amount) ? Role.SUPPORT_T2 : Role.FINANCE;
    }
}
