package com.rmrx.agenticshop.ai.contract.block;

import com.rmrx.agenticshop.ai.contract.response.ItemAction;
import com.rmrx.agenticshop.app.order.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** One order with its lines and totals. Line actions are per item (e.g. START_RETURN). */
public record OrderDetailBlock(String id, Long orderId, String orderNumber, OrderStatus status, Instant placedAt,
                               Instant shippedAt, Instant deliveredAt, List<Line> lines, BigDecimal subtotal,
                               BigDecimal shippingFee, BigDecimal tax, BigDecimal total, String currency,
                               List<ItemAction> actions) implements Block {

    public OrderDetailBlock {
        lines = lines == null ? List.of() : List.copyOf(lines);
        actions = actions == null ? List.of() : List.copyOf(actions);
    }

    public record Line(Long orderItemId, Long productId, String title, String sku, BigDecimal unitPrice,
                       int quantity, BigDecimal lineTotal, List<ItemAction> actions) {

        public Line {
            actions = actions == null ? List.of() : List.copyOf(actions);
        }
    }
}
