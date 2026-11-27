package com.rmrx.agenticshop.ai2.contract.tool.response.support;

import com.rmrx.agenticshop.ai2.contract.tool.ToolResponse;

/**
 * A ticket as it is after an action. {@code addedToOpenTicket}: a hand-off went into the customer's already open
 * ticket about the same matter instead of a new one.
 */
public record TicketActionResponse(TicketItem ticket, boolean addedToOpenTicket) implements ToolResponse {

    @Override
    public Object toAgentSummary() {
        return this;
    }
}
