package com.rmrx.agenticshop.ai.contract.block;

import java.util.List;

/** Products matching a search or recommendation. */
public record ProductListBlock(String id, List<ProductSummaryItem> items, long totalCount, boolean hasMore)
        implements Block {

    public ProductListBlock {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
