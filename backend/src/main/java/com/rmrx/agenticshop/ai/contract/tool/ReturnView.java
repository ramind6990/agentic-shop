package com.rmrx.agenticshop.ai.contract.tool;

import com.rmrx.agenticshop.app.returns.entity.ReturnReason;
import com.rmrx.agenticshop.app.returns.entity.ReturnStatus;

import java.time.Instant;

/** One return request. */
public record ReturnView(Long id, Long orderId, String orderNumber, Long orderItemId, String product, int qty,
                         ReturnReason reason, ReturnStatus status, Instant openedAt, Instant resolvedAt) {
}
