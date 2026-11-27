package com.rmrx.agenticshop.ai.contract.tool.response.returns;

import com.rmrx.agenticshop.ai.contract.tool.ToolResponse;

/** An issued refund. */
public record RefundResponse(RefundItem refund) implements ToolResponse {

    @Override
    public Object toAgentSummary() {
        return refund;
    }
}
