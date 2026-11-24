package com.rmrx.agenticshop.ai.contract.block;

import com.rmrx.agenticshop.ai.contract.response.ItemAction;
import com.rmrx.agenticshop.order.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** One order in an {@link OrderListBlock}. */
public record OrderSummaryItem(Long orderId, String orderNumber, OrderStatus status, Instant placedAt,
                               BigDecimal total, String currency, int itemCount, String firstItemTitle,
                               List<ItemAction> actions) {

    public OrderSummaryItem {
        actions = actions == null ? List.of() : List.copyOf(actions);
    }
}
