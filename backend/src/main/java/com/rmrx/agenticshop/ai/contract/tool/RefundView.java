package com.rmrx.agenticshop.ai.contract.tool;

import com.rmrx.agenticshop.order.entity.PaymentMethod;
import com.rmrx.agenticshop.returns.entity.RefundStatus;

import java.math.BigDecimal;
import java.time.Instant;

/** One refund; returnId is null for refunds not tied to a return. */
public record RefundView(Long id, Long orderId, Long returnId, BigDecimal amount, String currency,
                         RefundStatus status, PaymentMethod method, Instant createdAt) {
}
