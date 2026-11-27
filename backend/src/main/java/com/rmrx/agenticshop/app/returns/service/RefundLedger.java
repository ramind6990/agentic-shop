package com.rmrx.agenticshop.app.returns.service;

import com.rmrx.agenticshop.app.order.entity.CustomerOrder;
import com.rmrx.agenticshop.app.returns.entity.Refund;
import com.rmrx.agenticshop.app.returns.repository.RefundRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** How much of an order has been paid back, and how much can still be. */
@Component
public class RefundLedger {

    private final RefundRepository refunds;

    public RefundLedger(RefundRepository refunds) {
        this.refunds = refunds;
    }

    /** Order total minus what was already refunded (or is being refunded); never negative. */
    public BigDecimal remaining(CustomerOrder order) {
        BigDecimal refunded = refunds.findByOrderIdOrderByCreatedAtAsc(order.getId()).stream()
                .filter(Refund::countsAsRefunded)
                .map(Refund::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return order.getTotal().subtract(refunded).max(BigDecimal.ZERO);
    }
}
