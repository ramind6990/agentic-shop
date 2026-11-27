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
}
