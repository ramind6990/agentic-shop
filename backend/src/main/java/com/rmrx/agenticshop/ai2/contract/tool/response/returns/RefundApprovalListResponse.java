package com.rmrx.agenticshop.ai2.contract.tool.response.returns;

import com.rmrx.agenticshop.ai2.contract.tool.PageInfo;
import com.rmrx.agenticshop.ai2.contract.tool.ToolResponse;

import java.util.List;

/** A page of refund approval requests. */
public record RefundApprovalListResponse(List<RefundApprovalItem> items, PageInfo page) implements ToolResponse {

    public RefundApprovalListResponse {
        items = List.copyOf(items);
    }

    @Override
    public Object toAgentSummary() {
        return this;
    }

    @Override
    public List<String> itemIdentifiers() {
        return items.stream().map(RefundApprovalItem::approvalNumber).toList();
    }
}
