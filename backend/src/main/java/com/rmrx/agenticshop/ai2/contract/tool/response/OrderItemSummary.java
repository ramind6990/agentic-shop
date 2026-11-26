package com.rmrx.agenticshop.ai2.contract.tool.response;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What the model sees of one order: enough to reason about it and to pick it by its order number.
 */
public record OrderItemSummary(String orderNumber, LocalDate placedAt, OrderStatus status, BigDecimal total,
                               String currency, int itemCount, LocalDate estimatedDelivery) implements Serializable {
}
