package com.rmrx.agenticshop.ai2.contract.tool.response.returns;

import com.rmrx.agenticshop.ai2.contract.domain.RefundStatus;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

/** One refund (in the order's currency); {@code returnId} is set when it pays back a return. */
public record RefundItem(Long id, String orderNumber, BigDecimal amount, RefundStatus status, String reason,
                         Long returnId, Instant createdAt) implements Serializable {
}
