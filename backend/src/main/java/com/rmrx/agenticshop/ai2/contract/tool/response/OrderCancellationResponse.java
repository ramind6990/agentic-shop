package com.rmrx.agenticshop.ai2.contract.tool.response;

import com.rmrx.agenticshop.ai2.contract.tool.ToolResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * The result of cancelling an order; the model gets an {@link OrderCancellationSummary}.
 *
 * @param status           the order's status after the cancellation ({@code CANCELLED})
 * @param refundAmount     what goes back to the customer
 * @param refundMethod     where it goes back to, e.g. the original card
 * @param refundExpectedBy when the refund should have arrived
 */
public record OrderCancellationResponse(String orderNumber, OrderStatus status, Instant cancelledAt,
                                        BigDecimal refundAmount, String currency, String refundMethod,
                                        LocalDate refundExpectedBy) implements ToolResponse {

    @Override
    public OrderCancellationSummary toAgentSummary() {
        return new OrderCancellationSummary(orderNumber, status, refundAmount, currency, refundExpectedBy);
    }
}
