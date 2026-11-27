package com.rmrx.agenticshop.ai2.contract.tool.response.returns;

import com.rmrx.agenticshop.ai2.contract.domain.ReturnReason;
import com.rmrx.agenticshop.ai2.contract.domain.ReturnStatus;

import java.io.Serializable;
import java.time.Instant;

/** One return request: some units of one order line. */
public record ReturnItem(Long id, String orderNumber, Long itemId, String product, int quantity, ReturnReason reason,
                         ReturnStatus status, Instant openedAt, Instant resolvedAt, String customerNote,
                         String resolutionNote) implements Serializable {
}
