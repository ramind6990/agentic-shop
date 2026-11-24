package com.rmrx.agenticshop.ai.contract.block;

import com.rmrx.agenticshop.ai.contract.response.ItemAction;
import com.rmrx.agenticshop.returns.entity.RefundStatus;
import com.rmrx.agenticshop.returns.entity.ReturnReason;
import com.rmrx.agenticshop.returns.entity.ReturnStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** State of one return request; refund is null until a refund exists. */
public record ReturnStatusBlock(String id, Long returnId, Long orderId, String orderNumber, String productTitle,
                                int quantity, ReturnStatus status, ReturnReason reason, Instant openedAt,
                                Instant resolvedAt, RefundInfo refund, List<ItemAction> actions) implements Block {

    public ReturnStatusBlock {
        actions = actions == null ? List.of() : List.copyOf(actions);
    }

    public record RefundInfo(Long refundId, BigDecimal amount, String currency, RefundStatus status) {
    }
}
