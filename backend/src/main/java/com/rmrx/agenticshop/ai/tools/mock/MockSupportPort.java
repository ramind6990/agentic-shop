package com.rmrx.agenticshop.ai.tools.mock;

import com.rmrx.agenticshop.ai.context.AgentContext;
import com.rmrx.agenticshop.ai.contract.tool.TicketMessageView;
import com.rmrx.agenticshop.ai.contract.tool.WriteOutcome;
import com.rmrx.agenticshop.ai.tools.port.Page;
import com.rmrx.agenticshop.ai.tools.port.PortException;
import com.rmrx.agenticshop.ai.tools.port.SupportPort;
import com.rmrx.agenticshop.ai.tools.port.TicketView;
import com.rmrx.agenticshop.app.support.entity.AuthorType;
import com.rmrx.agenticshop.app.support.entity.SupportTier;
import com.rmrx.agenticshop.app.support.entity.TicketStatus;

import java.time.Instant;
import java.util.List;

/** {@link SupportPort} over the caller's {@link MockWorld}. */
public class MockSupportPort extends MockSupport implements SupportPort {

    public MockSupportPort(MockWorlds worlds) {
        super(worlds);
    }

    @Override
    public List<TicketMessageView> ticketHistory(AgentContext ctx, Long ticketId, int limit) {
        if (limit < 1) throw PortException.invalid("limit must be 1 or greater");
        int max = Math.min(limit, Page.MAX_SIZE);
        return inWorld(ctx, world -> {
            List<MockWorld.Message> visible = visibleTicket(world, ctx, ticketId).messages.stream()
                    .filter(m -> ctx.isStaff() || !m.internal).toList();
            return visible.subList(Math.max(0, visible.size() - max), visible.size()).stream()
                    .map(MockSupport::messageView).toList();
        });
    }

    @Override
    public WriteOutcome<TicketView> escalate(AgentContext ctx, Long ticketId, String reason) {
        if (reason == null || reason.isBlank()) throw PortException.invalid("reason is required");
        return inWorld(ctx, world -> {
            MockWorld.Ticket t = visibleTicket(world, ctx, ticketId);
            if (t.status == TicketStatus.CLOSED || t.status == TicketStatus.RESOLVED) {
                throw PortException.ineligible("ticket " + t.id + " is " + t.status);
            }
            if (t.status == TicketStatus.ESCALATED || t.tier == SupportTier.T2) {
                throw PortException.conflict("ticket " + t.id + " is already escalated to tier 2");
            }
            t.status = TicketStatus.ESCALATED;
            t.tier = SupportTier.T2;
            t.messages.add(message(world, AuthorType.SYSTEM, "system", "Escalated to tier 2: " + reason, false));
            return new WriteOutcome<>(true, new TicketView(t.id, t.subject, t.status, t.tier),
                    "Ticket " + t.id + " escalated to tier 2.");
        });
    }

    @Override
    public WriteOutcome<TicketMessageView> addNote(AgentContext ctx, Long ticketId, String text) {
        if (!ctx.isStaff()) throw PortException.forbidden("only staff can add internal notes");
        if (text == null || text.isBlank()) throw PortException.invalid("text is required");
        if (text.length() > 2000) throw PortException.invalid("text must be at most 2000 characters");
        return inWorld(ctx, world -> {
            MockWorld.Ticket t = visibleTicket(world, ctx, ticketId);
            MockWorld.Message m = message(world, AuthorType.STAFF, ctx.username(), text, true);
            t.messages.add(m);
            return new WriteOutcome<>(true, messageView(m), "Internal note added to ticket " + t.id + ".");
        });
    }

    private static MockWorld.Message message(MockWorld world, AuthorType type, String author, String body,
                                             boolean internal) {
        MockWorld.Message m = new MockWorld.Message();
        m.id = world.newId(MockWorld.IdKind.MESSAGE);
        m.authorType = type;
        m.author = author;
        m.body = body;
        m.internal = internal;
        m.at = Instant.now();
        return m;
    }
}
