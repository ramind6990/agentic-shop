package com.rmrx.agenticshop.ai2.contract.tool.response.order;

import com.rmrx.agenticshop.ai2.contract.domain.OrderStatus;
import com.rmrx.agenticshop.ai2.contract.domain.PaymentMethod;
import com.rmrx.agenticshop.ai2.contract.tool.ToolResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A cancelled order. {@code refundAmount} is zero (and the method null) when nothing had been charged yet: the
 * payment authorization was voided.
 */
public record OrderCancellationResponse(String orderNumber, OrderStatus status, Instant cancelledAt,
                                        BigDecimal refundAmount, String currency, PaymentMethod refundMethod,
                                        LocalDate refundExpectedBy) implements ToolResponse {

    @Override
    public Object toAgentSummary() {
        return this;
    }
}
