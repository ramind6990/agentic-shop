package com.rmrx.agenticshop.ai.contract.tool.response.order;

import com.rmrx.agenticshop.ai.contract.domain.OrderStatus;
import com.rmrx.agenticshop.ai.contract.tool.Texts;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** One order as a card shows it: its lines, delivery city and latest tracking. */
public record OrderItem(String orderNumber, Instant placedAt, OrderStatus status, BigDecimal total, String currency,
                        String seller, List<OrderLine> lines, String shippingCity, String trackingNumber,
                        LocalDate estimatedDelivery) implements Serializable {

    /** What the model sees of one order in a list. */
    public record Summary(String orderNumber, Instant placedAt, OrderStatus status, BigDecimal total,
                          String currency, int itemCount, List<String> itemTitles) implements Serializable {
    }

    public Summary toSummary() {
        return new Summary(orderNumber, placedAt, status, total, currency,
                lines.stream().mapToInt(OrderLine::quantity).sum(),
                lines.stream().limit(3).map(l -> Texts.cut(l.title(), 60)).toList());
    }
}
