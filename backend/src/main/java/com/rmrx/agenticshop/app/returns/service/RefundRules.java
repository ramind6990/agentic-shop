package com.rmrx.agenticshop.app.returns.service;

import com.rmrx.agenticshop.app.identity.entity.Role;
import com.rmrx.agenticshop.app.order.entity.CustomerOrder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/** The rules for refunding an amount on an order, asked the same way by the checks and the commands. */
@Component
public class RefundRules {

    private final RefundLedger ledger;

    public RefundRules(RefundLedger ledger) {
        this.ledger = ledger;
    }

    public RefundCheck check(Role role, CustomerOrder order, BigDecimal amount) {
        String currency = order.getCurrency();
        BigDecimal left = ledger.remaining(order);
        if (amount == null || amount.signum() <= 0) {
            return RefundCheck.rejected("The amount must be positive.", amount, left, currency);
        }
        if (amount.compareTo(left) > 0) {
            return RefundCheck.rejected("Only " + left + " " + currency + " of this order can still be refunded.",
                    amount, left, currency);
        }
        Optional<BigDecimal> cap = RefundLimits.capFor(role);
        if (cap.isPresent() && amount.compareTo(cap.get()) > 0) {
            return RefundCheck.needsApproval(amount, left, cap.get(), currency);
        }
        return RefundCheck.allowed(amount, left, cap.orElse(null), currency);
    }
}
