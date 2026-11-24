package com.rmrx.agenticshop.ai.tools;

import com.rmrx.agenticshop.ai.context.AgentContext;
import com.rmrx.agenticshop.ai.contract.tool.TicketMessageView;
import com.rmrx.agenticshop.ai.escalation.EscalationService;
import com.rmrx.agenticshop.ai.tools.port.PortException;
import com.rmrx.agenticshop.ai.tools.port.SupportPort;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * Tools over the support ticket bound to this conversation: its history, and the hand-off to a human. Escalation
 * changes our own ticket row through {@link EscalationService} (not {@link SupportPort#escalate}, whose mock works on
 * the mock world's tickets; an external helpdesk adapter can hook in there later).
 */
public class SupportTools extends AbstractTools {

    public static final String GET_TICKET_HISTORY = "get_ticket_history";
    public static final String ESCALATE_TO_HUMAN = "escalate_to_human";

    private final SupportPort support;
    private final EscalationService escalations;

    SupportTools(AgentContext ctx, TurnResultRegistry registry, JsonMapper json, SupportPort support,
                 EscalationService escalations) {
        super(ctx, registry, json);
        this.support = support;
        this.escalations = escalations;
    }

    @Tool(name = ESCALATE_TO_HUMAN, value = "Hands this conversation to a human support agent: the ticket is "
            + "escalated and the UI shows the hand-off. Use it when the user asks for a person, when you cannot give "
            + "a grounded answer after looking things up, or when a rule says the case needs manual handling (e.g. "
            + "LIMIT_EXCEEDED). Escalating twice changes nothing. Afterwards, tell the user a person will follow up "
            + "here; do not promise times or outcomes beyond what the result says.")
    @ToolLabel("Handing over to a support agent…")
    public String escalateToHuman(@P("One short sentence on why, for the support agent") String reason) {
        return run(ESCALATE_TO_HUMAN, () -> escalate(ctx, requireReason(reason), EscalationService.Trigger.AGENT_TOOL));
    }

    private String requireReason(String reason) {
        if (reason == null || reason.isBlank()) throw PortException.invalid("reason is required");
        return reason;
    }

    /** Escalates and returns the tool data (the caller registers it). */
    ToolData.Escalated escalate(AgentContext ctx, String reason, EscalationService.Trigger trigger) {
        if (ctx.ticketId() == null) throw PortException.invalid("no support ticket is bound to this conversation");
        try {
            return toData(escalations.escalate(ctx, reason, trigger, null));
        } catch (IllegalStateException e) {
            throw PortException.ineligible("this ticket is closed and cannot be escalated");
        }
    }

    public static ToolData.Escalated toData(EscalationService.Outcome o) {
        return new ToolData.Escalated(o.ticketId(), o.ticketNumber(), o.tier(), o.status(), o.reason(),
                EscalationService.EXPECTED_RESPONSE, o.alreadyEscalated());
    }

    @Tool(name = GET_TICKET_HISTORY, value = "Gets the latest messages of this conversation's support ticket, "
            + "oldest first, to recall what was already said or done.")
    @ToolLabel("Reading the ticket history\u2026")
    public String getTicketHistory(
            @P(value = "How many recent messages, 1 to 20; default 10", required = false) Integer limit) {
        return run(GET_TICKET_HISTORY, () -> {
            if (ctx.ticketId() == null) throw PortException.invalid("no support ticket is bound to this conversation");
            int n = limit == null ? PAGE_SIZE : limit;
            if (n < 1 || n > 20) throw PortException.invalid("limit must be between 1 and 20");
            List<TicketMessageView> messages = support.ticketHistory(ctx, ctx.ticketId(), n);
            return new ToolData.TicketHistory(ctx.ticketId(), messages, messages == null ? 0 : messages.size());
        });
    }
}
