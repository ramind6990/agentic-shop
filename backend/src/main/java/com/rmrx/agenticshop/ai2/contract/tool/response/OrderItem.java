package com.rmrx.agenticshop.ai2.contract.tool.response;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * One order with the data the reply shows. The model gets the smaller {@link OrderItemSummary}.
 *
 * @param orderNumber       the customer-facing id, e.g. {@code WE45}
 * @param estimatedDelivery null once delivered or cancelled
 * @param trackingNumber    null until shipped
 */
public record OrderItem(String orderNumber, LocalDate placedAt, OrderStatus status, BigDecimal total, String currency,
                        List<OrderLine> lines, String shippingCity, String trackingNumber,
                        LocalDate estimatedDelivery) implements Serializable {

    public OrderItem {
        lines = List.copyOf(lines);
    }

    public OrderItemSummary toSummary() {
        int itemCount = lines.stream().mapToInt(OrderLine::quantity).sum();
        return new OrderItemSummary(orderNumber, placedAt, status, total, currency, itemCount, estimatedDelivery);
    }
}
