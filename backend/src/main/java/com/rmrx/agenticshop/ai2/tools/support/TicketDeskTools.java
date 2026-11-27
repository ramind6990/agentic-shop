package com.rmrx.agenticshop.ai2.tools.support;

import com.rmrx.agenticshop.ai2.contract.tool.response.support.TicketActionResponse;
import com.rmrx.agenticshop.ai2.tools.AgentToolSet;
import com.rmrx.agenticshop.ai2.tools.Audience;
import com.rmrx.agenticshop.ai2.tools.Sensitive;
import com.rmrx.agenticshop.ai2.tools.ToolAudience;
import com.rmrx.agenticshop.ai2.tools.ToolLabel;
import com.rmrx.agenticshop.app.support.dto.TicketDtos.TicketDetail;
import com.rmrx.agenticshop.app.support.service.TicketCommandService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

/**
 * The support desk's work on a ticket. Internal steps (take, note, escalate) run at once; what the customer reads
 * (reply, resolve) is approved by the support agent first.
 */
@Component
@ToolAudience(Audience.STAFF)
public class TicketDeskTools implements AgentToolSet {

    public static final String TAKE_TICKET = "take_ticket";
    public static final String ADD_INTERNAL_NOTE = "add_internal_note";
    public static final String REPLY_TO_TICKET = "reply_to_ticket";
    public static final String ESCALATE_TICKET = "escalate_ticket";
    public static final String RESOLVE_TICKET = "resolve_ticket";

    private final TicketCommandService commands;
    private final SupportToolMapper mapper;

    public TicketDeskTools(TicketCommandService commands, SupportToolMapper mapper) {
        this.commands = commands;
        this.mapper = mapper;
    }

    @ToolLabel("Taking ticket {ticketNumber}")
    @Tool(name = TAKE_TICKET, value = "Assign an open ticket to yourself, so others see it is being worked on.")
    public TicketActionResponse takeTicket(@P("The ticket number") String ticketNumber) {
        return done(commands.take(ticketNumber));
    }

    @ToolLabel("Adding a note to ticket {ticketNumber}")
    @Tool(name = ADD_INTERNAL_NOTE, value = "Add a staff-only note to a ticket (findings, decisions); the customer "
            + "never sees it.")
    public TicketActionResponse addInternalNote(@P("The ticket number") String ticketNumber,
                                                @P("The note") String note) {
        return done(commands.note(ticketNumber, note));
    }

    @ToolLabel("Replying on ticket {ticketNumber}")
    @Sensitive(approvalMessage = "Send this reply to the customer on ticket {ticketNumber}?", detail = "{message}")
    @Tool(name = REPLY_TO_TICKET, value = "Send a message the customer reads on their ticket; the ticket then waits "
            + "for the customer (PENDING). Write it to the customer, in plain friendly words. You approve it before "
            + "it is sent.")
    public TicketActionResponse replyToTicket(@P("The ticket number") String ticketNumber,
                                              @P("The message to the customer") String message) {
        return done(commands.reply(ticketNumber, message));
    }

    @ToolLabel("Escalating ticket {ticketNumber} to T2")
    @Tool(name = ESCALATE_TICKET, value = "Hand a T1 ticket to the second tier (supervisors), e.g. when a refund is "
            + "above your limit or an exception is needed. The reason is kept as an internal note; the ticket goes "
            + "back to the T2 queue unassigned.")
    public TicketActionResponse escalateTicket(@P("The ticket number") String ticketNumber,
                                               @P("Why T2 is needed") String reason) {
        return done(commands.escalate(ticketNumber, reason));
    }

    @ToolLabel("Resolving ticket {ticketNumber}")
    @Sensitive(approvalMessage = "Resolve ticket {ticketNumber} with this message to the customer?",
            detail = "{resolution}")
    @Tool(name = RESOLVE_TICKET, value = "Close a ticket as resolved, with a final message the customer reads (what "
            + "was done for them). You approve it before it is sent.")
    public TicketActionResponse resolveTicket(@P("The ticket number") String ticketNumber,
                                              @P("The resolution, written to the customer") String resolution) {
        return done(commands.resolve(ticketNumber, resolution));
    }

    private TicketActionResponse done(TicketDetail ticket) {
        return new TicketActionResponse(mapper.toItem(ticket), false);
    }
}
