package com.rmrx.agenticshop.ai2.contract.tool.response.returns;

import com.rmrx.agenticshop.ai2.contract.tool.PageInfo;
import com.rmrx.agenticshop.ai2.contract.tool.ToolResponse;

import java.util.List;

/** A page of refunds, newest first. */
public record RefundListResponse(List<RefundItem> items, PageInfo page) implements ToolResponse {

    public RefundListResponse {
        items = List.copyOf(items);
    }

    @Override
    public Object toAgentSummary() {
        return this;
    }

    @Override
    public List<String> itemIdentifiers() {
        return items.stream().map(r -> String.valueOf(r.id())).toList();
    }
}
