package com.rmrx.agenticshop.ai2.contract.tool.response;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What the model sees of an {@link OrderCancellationResponse}.
 */
public record OrderCancellationSummary(String orderNumber, OrderStatus status, BigDecimal refundAmount,
                                       String currency, LocalDate refundExpectedBy) implements Serializable {
}
