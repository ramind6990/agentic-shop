package com.rmrx.agenticshop.ai.contract.tool.response.support;

import com.rmrx.agenticshop.ai.contract.tool.PageInfo;
import com.rmrx.agenticshop.ai.contract.tool.ToolResponse;

import java.util.List;

/** A page of support tickets, most recently updated first. */
public record TicketListResponse(List<TicketItem> items, PageInfo page) implements ToolResponse {

    public TicketListResponse {
        items = List.copyOf(items);
    }

    @Override
    public Object toAgentSummary() {
        return this;
    }

    @Override
    public List<String> itemIdentifiers() {
        return items.stream().map(TicketItem::ticketNumber).toList();
    }
}
