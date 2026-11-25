package com.rmrx.agenticshop.ai.contract.block;

import com.rmrx.agenticshop.app.order.entity.PaymentMethod;
import com.rmrx.agenticshop.app.returns.entity.RefundStatus;

import java.math.BigDecimal;

/** Result of a refund write: what was refunded and to which payment method. */
public record RefundResultBlock(String id, Long refundId, Long orderId, BigDecimal amount, String currency,
                                RefundStatus status, PaymentMethod method) implements Block {
}
