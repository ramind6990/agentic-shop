package com.rmrx.agenticshop.ai.contract.tool;

import com.rmrx.agenticshop.order.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

/** An order as a list tool returns it. */
public record OrderSummaryView(Long id, String number, OrderStatus status, Instant placedAt, BigDecimal total,
                               String currency, int itemCount, String firstItem) {
}
