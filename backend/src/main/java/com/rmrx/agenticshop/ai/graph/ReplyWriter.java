package com.rmrx.agenticshop.ai.graph;

import com.rmrx.agenticshop.ai.contract.response.AgentResponse;
import com.rmrx.agenticshop.ai.contract.response.ResponseStatus;
import com.rmrx.agenticshop.common.exception.NotFoundException;
import com.rmrx.agenticshop.support.entity.Ticket;
import com.rmrx.agenticshop.support.entity.TicketMessage;
import com.rmrx.agenticshop.support.repository.TicketMessageRepository;
import com.rmrx.agenticshop.support.repository.TicketRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Saves agent replies as ENVELOPE ticket messages (author AI, body = the reply text, payload = the full
 * {@link AgentResponse} JSON with its schemaVersion), each in its own transaction. The saved response carries its
 * messageId, so the payload and the FINAL event are identical.
 */
@Component
public class ReplyWriter {

    public static final String ERROR_TEXT = "Sorry, something went wrong while I was working on this. "
            + "Please try again in a moment.";

    private final TicketRepository tickets;
    private final TicketMessageRepository messages;
    private final JsonMapper json;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    @PersistenceContext
    private EntityManager entityManager;

    public ReplyWriter(TicketRepository tickets, TicketMessageRepository messages, JsonMapper json,
                       JdbcTemplate jdbc, PlatformTransactionManager tm) {
        this.tickets = tickets;
        this.messages = messages;
        this.json = json;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(tm);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Persists the reply and returns it with its messageId set. */
    public AgentResponse save(Long ticketId, AgentResponse response, boolean internal) {
        return tx.execute(status -> {
            Ticket ticket = tickets.findById(ticketId).orElseThrow(() -> new NotFoundException("Ticket", ticketId));
            Instant now = Instant.now();
            TicketMessage m = messages.saveAndFlush(TicketMessage.envelope(ticket, response.text(),
                    json.valueToTree(response), response.schemaVersion(), internal, now));
            AgentResponse saved = new AgentResponse(response.schemaVersion(), response.status(), response.text(),
                    response.blocks(), response.citations(), response.suggestedReplies(), response.runId(),
                    m.getId(), response.createdAt());
            // the id exists only after the insert; store the payload again so it carries its messageId (detached
            // first, otherwise Hibernate's JSON dirty check writes the old payload back at commit)
            entityManager.detach(m);
            jdbc.update("update ticket_message set payload = cast(? as jsonb) where id = ?",
                    json.writeValueAsString(saved), m.getId());
            ticket.touch(now);
            return saved;
        });
    }

    /** The reply persisted when a turn fails, so the timeline stays coherent. */
    public static AgentResponse errorResponse(UUID runId, String text) {
        return new AgentResponse(AgentResponse.CURRENT_SCHEMA_VERSION, ResponseStatus.ERROR,
                text == null ? ERROR_TEXT : text, List.of(), List.of(), List.of(), runId, null, Instant.now());
    }
}
