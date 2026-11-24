package com.rmrx.agenticshop.ai.contract.block;

import java.util.List;

/** A page of the user's orders; hasMore tells the UI more exist than were shown. */
public record OrderListBlock(String id, List<OrderSummaryItem> items, long totalCount, boolean hasMore)
        implements Block {

    public OrderListBlock {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
