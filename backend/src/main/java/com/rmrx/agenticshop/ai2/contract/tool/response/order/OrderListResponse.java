package com.rmrx.agenticshop.ai2.contract.tool.response.order;

import com.rmrx.agenticshop.ai2.contract.tool.PageInfo;
import com.rmrx.agenticshop.ai2.contract.tool.ToolResponse;

import java.io.Serializable;
import java.util.List;

/** A page of orders, newest first. */
public record OrderListResponse(List<OrderItem> items, PageInfo page) implements ToolResponse {

    public OrderListResponse {
        items = List.copyOf(items);
    }

    public record Summary(List<OrderItem.Summary> items, PageInfo page) implements Serializable {
    }

    @Override
    public Summary toAgentSummary() {
        return new Summary(items.stream().map(OrderItem::toSummary).toList(), page);
    }

    @Override
    public List<String> itemIdentifiers() {
        return items.stream().map(OrderItem::orderNumber).toList();
    }
}
