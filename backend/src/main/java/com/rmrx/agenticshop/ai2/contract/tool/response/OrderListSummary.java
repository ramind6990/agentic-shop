package com.rmrx.agenticshop.ai2.contract.tool.response;

import java.io.Serializable;
import java.util.List;

/**
 * What the model sees of an {@link OrderListResponse}.
 */
public record OrderListSummary(List<OrderItemSummary> items, int total, boolean hasMore) implements Serializable {

    public OrderListSummary {
        items = List.copyOf(items);
    }
}
