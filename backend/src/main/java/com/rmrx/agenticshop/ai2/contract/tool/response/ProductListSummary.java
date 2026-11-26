package com.rmrx.agenticshop.ai2.contract.tool.response;

import java.io.Serializable;
import java.util.List;

/**
 * What the model sees of a {@link ProductListResponse}.
 */
public record ProductListSummary(List<ProductItemSummary> items, int total, boolean hasMore) implements Serializable {

    public ProductListSummary {
        items = List.copyOf(items);
    }
}
