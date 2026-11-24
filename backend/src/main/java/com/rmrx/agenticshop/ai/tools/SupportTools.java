package com.rmrx.agenticshop.ai.tools;

import com.rmrx.agenticshop.ai.context.AgentContext;
import com.rmrx.agenticshop.ai.contract.tool.TicketMessageView;
import com.rmrx.agenticshop.ai.tools.port.PortException;
import com.rmrx.agenticshop.ai.tools.port.SupportPort;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/** Read tools over the support ticket bound to this conversation. */
public class SupportTools extends AbstractTools {

    public static final String GET_TICKET_HISTORY = "get_ticket_history";

    private final SupportPort support;

    SupportTools(AgentContext ctx, TurnResultRegistry registry, JsonMapper json, SupportPort support) {
        super(ctx, registry, json);
        this.support = support;
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
