package com.rmrx.agenticshop.ai2.contract.tool.response;

import com.rmrx.agenticshop.ai2.contract.tool.ToolResponse;

import java.util.List;

/**
 * A page of the customer's orders with their full data; the model gets an {@link OrderListSummary}. Items are
 * identified by order number.
 *
 * @param total   all matching orders, not only the returned items
 * @param hasMore whether more matching orders exist beyond {@code items}
 */
public record OrderListResponse(List<OrderItem> items, int total, boolean hasMore) implements ToolResponse {

    public OrderListResponse {
        items = List.copyOf(items);
    }

    @Override
    public OrderListSummary toAgentSummary() {
        return new OrderListSummary(items.stream().map(OrderItem::toSummary).toList(), total, hasMore);
    }

    @Override
    public List<String> itemIdentifiers() {
        return items.stream().map(OrderItem::orderNumber).toList();
    }
}
