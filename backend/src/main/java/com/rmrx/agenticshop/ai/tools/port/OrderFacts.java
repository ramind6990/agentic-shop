package com.rmrx.agenticshop.ai.tools.port;

import com.rmrx.agenticshop.order.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Current state of one order as the action engine needs it to choose buttons: owner, status, whether anything has
 * shipped, what is still refundable, and per visible line when it was delivered (null if not yet) and whether it
 * already has a return that blocks a new one. Never shown to the user or the model.
 */
public record OrderFacts(Long orderId, String orderNumber, Long customerId, OrderStatus status, int shipmentCount,
                         BigDecimal refundable, String currency, List<ItemFacts> items) {

    public OrderFacts {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public record ItemFacts(Long itemId, Instant deliveredAt, boolean hasActiveReturn) {
    }
}
