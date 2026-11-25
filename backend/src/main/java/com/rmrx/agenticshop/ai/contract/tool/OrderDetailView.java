package com.rmrx.agenticshop.ai.contract.tool;

import com.rmrx.agenticshop.app.order.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** One order with its lines and shipments, as a detail tool returns it. */
public record OrderDetailView(Long id, String number, OrderStatus status, Instant placedAt, List<Line> lines,
                              BigDecimal subtotal, BigDecimal shippingFee, BigDecimal tax, BigDecimal total,
                              String currency, List<ShipmentView> shipments) {

    public OrderDetailView {
        lines = lines == null ? List.of() : List.copyOf(lines);
        shipments = shipments == null ? List.of() : List.copyOf(shipments);
    }

    public record Line(Long itemId, Long productId, String title, String sku, BigDecimal unitPrice, int qty,
                       BigDecimal lineTotal) {
    }
}
