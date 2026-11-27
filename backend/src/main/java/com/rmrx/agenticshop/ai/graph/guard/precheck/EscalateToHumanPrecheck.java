package com.rmrx.agenticshop.ai.graph.guard.precheck;

import com.rmrx.agenticshop.ai.tools.support.TicketTools;
import com.rmrx.agenticshop.app.support.service.TicketCommandService;
import org.springframework.stereotype.Component;

/**
 * {@code escalate_to_human}: the order it names must exist for the customer, so the customer never approves a
 * hand-off that then fails (the model retries without the wrong number).
 */
@Component
class EscalateToHumanPrecheck implements SensitivePrecheck {

    private final TicketCommandService tickets;

    EscalateToHumanPrecheck(TicketCommandService tickets) {
        this.tickets = tickets;
    }

    @Override
    public String toolName() {
        return TicketTools.ESCALATE_TO_HUMAN;
    }

    @Override
    public PrecheckResult check(PrecheckArgs args) {
        return tickets.checkOpen(args.text("orderNumber"))
                .map(PrecheckResult::blocked)
                .orElseGet(() -> PrecheckResult.allowed(null));
    }
}
