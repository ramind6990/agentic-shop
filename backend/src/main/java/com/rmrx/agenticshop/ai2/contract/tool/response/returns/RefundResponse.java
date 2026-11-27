package com.rmrx.agenticshop.ai2.contract.tool.response.returns;

import com.rmrx.agenticshop.ai2.contract.tool.ToolResponse;

/** An issued refund. */
public record RefundResponse(RefundItem refund) implements ToolResponse {

    @Override
    public Object toAgentSummary() {
        return refund;
    }
}
