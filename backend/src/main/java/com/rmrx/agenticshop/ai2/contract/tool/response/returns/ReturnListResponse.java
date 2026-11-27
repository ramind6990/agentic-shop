package com.rmrx.agenticshop.ai2.contract.tool.response.returns;

import com.rmrx.agenticshop.ai2.contract.tool.PageInfo;
import com.rmrx.agenticshop.ai2.contract.tool.ToolResponse;

import java.util.List;

/** A page of return requests, newest first. */
public record ReturnListResponse(List<ReturnItem> items, PageInfo page) implements ToolResponse {

    public ReturnListResponse {
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
