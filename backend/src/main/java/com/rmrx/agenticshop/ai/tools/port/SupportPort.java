package com.rmrx.agenticshop.ai.tools.port;

import com.rmrx.agenticshop.ai.context.AgentContext;
import com.rmrx.agenticshop.ai.contract.tool.TicketMessageView;
import com.rmrx.agenticshop.ai.contract.tool.WriteOutcome;

import java.util.List;

/** Support tickets: customers see their own tickets without internal notes, staff see all, sellers none. */
public interface SupportPort {

    /** The latest messages, oldest first, at most limit (capped at {@link Page#MAX_SIZE}). */
    List<TicketMessageView> ticketHistory(AgentContext ctx, Long ticketId, int limit);

    /** Moves the ticket to tier 2: CONFLICT if it is already escalated, INELIGIBLE if closed. */
    WriteOutcome<TicketView> escalate(AgentContext ctx, Long ticketId, String reason);

    /** Adds an internal note; staff only (FORBIDDEN otherwise). */
    WriteOutcome<TicketMessageView> addNote(AgentContext ctx, Long ticketId, String text);
}
