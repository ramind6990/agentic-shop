package com.rmrx.agenticshop.ai2.contract.tool.response;

import com.rmrx.agenticshop.ai2.contract.tool.ToolResponse;

import java.util.List;

/**
 * A page of products with their full data; the model gets a {@link ProductListSummary}.
 *
 * @param total   all matches, not only the returned items
 * @param hasMore whether more matches exist beyond {@code items}
 */
public record ProductListResponse(List<ProductItem> items, int total, boolean hasMore) implements ToolResponse {

    public ProductListResponse {
        items = List.copyOf(items);
    }

    @Override
    public ProductListSummary toAgentSummary() {
        return new ProductListSummary(items.stream().map(ProductItem::toSummary).toList(), total, hasMore);
    }

    @Override
    public List<String> itemIdentifiers() {
        return items.stream().map(p -> String.valueOf(p.id())).toList();
    }
}
