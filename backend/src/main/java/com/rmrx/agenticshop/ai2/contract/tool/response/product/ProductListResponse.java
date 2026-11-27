package com.rmrx.agenticshop.ai2.contract.tool.response.product;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rmrx.agenticshop.ai2.contract.tool.PageInfo;
import com.rmrx.agenticshop.ai2.contract.tool.ToolResponse;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * A page of products found by a search, with refinements (null when the result fits one page) that tell how to
 * narrow a big result: the categories and brands the matches fall into, with counts, and their price range.
 */
public record ProductListResponse(List<ProductItem> items, PageInfo page, Refinements refinements)
        implements ToolResponse {

    public ProductListResponse {
        items = List.copyOf(items);
    }

    public record Facet(Long id, String name, long count) implements Serializable {
    }

    public record Refinements(List<Facet> categories, List<Facet> brands, BigDecimal minPrice, BigDecimal maxPrice)
            implements Serializable {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Summary(List<ProductItem.Summary> items, PageInfo page, Refinements refinements)
            implements Serializable {
    }

    @Override
    public Summary toAgentSummary() {
        return new Summary(items.stream().map(ProductItem::toSummary).toList(), page, refinements);
    }

    @Override
    public List<String> itemIdentifiers() {
        return items.stream().map(p -> String.valueOf(p.id())).toList();
    }
}
