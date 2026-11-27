package com.rmrx.agenticshop.app.returns.service;

import com.rmrx.agenticshop.app.common.exception.RuleViolationException;
import com.rmrx.agenticshop.app.identity.entity.Role;

import java.math.BigDecimal;

/**
 * Whether a refund of an amount on an order may be issued by the caller now (checked without changing anything).
 *
 * @param remaining    what can still be refunded on the order
 * @param cap          the caller's limit per refund; null = no limit
 * @param approverRole NEEDS_APPROVAL only: the lowest role whose limit covers the amount
 */
public record RefundCheck(Outcome outcome, String reason, BigDecimal amount, BigDecimal remaining, BigDecimal cap,
                          Role approverRole, String currency) {

    public enum Outcome {
        /** Within the caller's limit and what is left on the order. */
        ALLOWED,
        /** Within what is left on the order, but above the caller's limit: a colleague has to approve it. */
        NEEDS_APPROVAL,
        /** Not possible at all (amount not positive, more than is left, ...). */
        REJECTED
    }

    static RefundCheck rejected(String reason, BigDecimal amount, BigDecimal remaining, String currency) {
        return new RefundCheck(Outcome.REJECTED, reason, amount, remaining, null, null, currency);
    }

    static RefundCheck needsApproval(BigDecimal amount, BigDecimal remaining, BigDecimal cap, String currency) {
        Role approver = RefundLimits.approverFor(amount);
        return new RefundCheck(Outcome.NEEDS_APPROVAL, "Your role may refund at most " + cap + " " + currency
                + " per refund; " + amount + " " + currency + " needs the approval of a colleague with role "
                + approver + ".", amount, remaining, cap, approver, currency);
    }

    static RefundCheck allowed(BigDecimal amount, BigDecimal remaining, BigDecimal cap, String currency) {
        return new RefundCheck(Outcome.ALLOWED, null, amount, remaining, cap, null, currency);
    }

    /** Throws unless the caller may issue the refund now. */
    public void requireAllowed() {
        if (outcome != Outcome.ALLOWED) throw new RuleViolationException(reason);
    }
}
