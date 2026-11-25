package com.rmrx.agenticshop.ai.escalation;

import com.rmrx.agenticshop.ai.context.AgentContext;
import com.rmrx.agenticshop.app.common.exception.NotFoundException;
import com.rmrx.agenticshop.app.support.entity.SupportTier;
import com.rmrx.agenticshop.app.support.entity.Ticket;
import com.rmrx.agenticshop.app.support.entity.TicketMessage;
import com.rmrx.agenticshop.app.support.entity.TicketStatus;
import com.rmrx.agenticshop.app.support.repository.TicketMessageRepository;
import com.rmrx.agenticshop.app.support.repository.TicketRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Hands a conversation to a human. Works on our own {@code ticket} table (not through a port): in one transaction the
 * ticket moves to status ESCALATED and tier T2, a SYSTEM message "Escalated to a human agent: &lt;reason&gt;" is added
 * (the model sees it as a system note in later turns), and every OPEN action token of the ticket is SUPERSEDED, so
 * old buttons can no longer start anything.
 *
 * <p>Idempotent: the status change is a conditional update ({@code status <> 'ESCALATED'}), so a second escalation
 * (another trigger, a retry, a race) changes nothing, adds no message and returns {@code alreadyEscalated=true}. A
 * CLOSED ticket cannot be escalated ({@link IllegalStateException}).
 *
 * <p>After this, customers' messages on the ticket are stored but get no AI turn (see ConversationService); staff can
 * still use the assistant there as an internal copilot.
 */
@Slf4j
@Service
public class EscalationService {

    public static final SupportTier ESCALATED_TIER = SupportTier.T2;
    public static final String EXPECTED_RESPONSE = "A support specialist will reply in this conversation, usually "
            + "within 4 business hours. You will see their answer here.";
    public static final int REASON_MAX = 300;
    public static final int PAYLOAD_SCHEMA_VERSION = 1;

    /** What triggered the escalation; stored in the SYSTEM message payload and the trace. */
    public enum Trigger {
        /** The model called escalate_to_human (user asked for a person, no grounded answer, manual policy). */
        AGENT_TOOL,
        /** The user clicked the TALK_TO_HUMAN button. */
        USER_BUTTON,
        /** Code: repeated UNAVAILABLE/TIMEOUT tool failures in one turn. */
        AUTO_TOOL_FAILURES,
        /** Code: the turn's step budget was used up. */
        AUTO_STEP_BUDGET
    }

    public record Outcome(Long ticketId, String ticketNumber, TicketStatus status, SupportTier tier, String reason,
                          boolean alreadyEscalated, int supersededActions, Long systemMessageId) {
    }

    private final TicketRepository tickets;
    private final TicketMessageRepository messages;
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final TransactionTemplate tx;

    public EscalationService(TicketRepository tickets, TicketMessageRepository messages, JdbcTemplate jdbc,
                             JsonMapper json, PlatformTransactionManager tm) {
        this.tickets = tickets;
        this.messages = messages;
        this.jdbc = jdbc;
        this.json = json;
        this.tx = new TransactionTemplate(tm);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Escalates ctx's ticket. reason is a short plain sentence (clipped); runId may be null. */
    public Outcome escalate(AgentContext ctx, String reason, Trigger trigger, UUID runId) {
        Long ticketId = ctx.ticketId();
        if (ticketId == null) throw new IllegalArgumentException("no ticket bound to this conversation");
        String why = clean(reason);
        return tx.execute(status -> {
            Instant now = Instant.now();
            int changed = jdbc.update("update ticket set status = ?, tier = ?, updated_at = ? "
                            + "where id = ? and status not in (?, ?)", TicketStatus.ESCALATED.name(),
                    ESCALATED_TIER.name(), Timestamp.from(now), ticketId, TicketStatus.ESCALATED.name(),
                    TicketStatus.CLOSED.name());
            Ticket t = tickets.findById(ticketId).orElseThrow(() -> new NotFoundException("Ticket", ticketId));
            if (changed == 0) {
                if (t.getStatus() == TicketStatus.CLOSED) throw new IllegalStateException("ticket is closed");
                log.info("Ticket {} already escalated; escalation by {} ({}) ignored", ticketId, trigger, why);
                return new Outcome(t.getId(), t.getTicketNumber(), t.getStatus(), t.getTier(), why, true, 0, null);
            }
            ObjectNode payload = json.createObjectNode();
            payload.put("event", "ESCALATED");
            payload.put("trigger", trigger.name());
            payload.put("reason", why);
            payload.put("tier", ESCALATED_TIER.name());
            if (runId != null) payload.put("runId", runId.toString());
            payload.put("byUserId", ctx.userId());
            // a staff copilot's escalation reason may be internal, so it stays a staff note
            TicketMessage m = messages.saveAndFlush(TicketMessage.system(t, "Escalated to a human agent: " + why,
                    payload, PAYLOAD_SCHEMA_VERSION, ctx.isStaff(), now));
            int superseded = jdbc.update("update ai_action set state = 'SUPERSEDED' where ticket_id = ? "
                    + "and state = 'OPEN'", ticketId);
            log.info("Ticket {} escalated to {} by {} (run {}): {}; {} open actions superseded", ticketId,
                    ESCALATED_TIER, trigger, runId, why, superseded);
            return new Outcome(t.getId(), t.getTicketNumber(), TicketStatus.ESCALATED, ESCALATED_TIER, why, false,
                    superseded, m.getId());
        });
    }

    private static String clean(String reason) {
        String r = reason == null ? "" : reason.strip().replaceAll("\s+", " ");
        if (r.isEmpty()) r = "The customer needs help from a person.";
        return r.length() <= REASON_MAX ? r : r.substring(0, REASON_MAX - 1) + "…";
    }
}
