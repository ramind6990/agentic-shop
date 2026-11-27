package com.rmrx.agenticshop.ai2.contract.tool.response.returns;

import com.rmrx.agenticshop.ai2.contract.tool.ToolResponse;

/** A return request as it is after an action (opened, withdrawn, approved or rejected). */
public record ReturnResponse(ReturnItem returnRequest) implements ToolResponse {

    @Override
    public Object toAgentSummary() {
        return returnRequest;
    }
}
