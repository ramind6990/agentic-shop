package com.rmrx.agenticshop.app.returns.dto;

import com.rmrx.agenticshop.app.common.dto.Ref;
import com.rmrx.agenticshop.app.returns.entity.RefundStatus;
import com.rmrx.agenticshop.app.returns.entity.ReturnReason;
import com.rmrx.agenticshop.app.returns.entity.ReturnStatus;

import java.math.BigDecimal;
import java.time.Instant;

public final class ReturnDtos {

    private ReturnDtos() {
    }

    public record OrderRef(Long id, String orderNumber) {
    }

    public record ReturnDto(Long id, OrderRef order, Long orderItemId, Ref product, Ref customer, int quantity,
                            ReturnReason reason, ReturnStatus status, String customerNote, String sellerNote,
                            Instant openedAt, Instant resolvedAt, String resolutionNote) {
    }

    public record RefundDto(Long id, OrderRef order, Long returnRequestId, BigDecimal amount, String reason,
                            RefundStatus status, Ref issuedBy, Instant createdAt) {
    }
}
