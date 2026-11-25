package com.rmrx.agenticshop.ai.web.dev;

import com.rmrx.agenticshop.ai.action.entity.AiAction;
import com.rmrx.agenticshop.ai.contract.response.ActionType;
import com.rmrx.agenticshop.app.support.entity.AuthorType;
import com.rmrx.agenticshop.app.support.entity.MessageKind;
import com.rmrx.agenticshop.app.support.entity.Ticket;
import com.rmrx.agenticshop.app.support.entity.TicketMessage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.type.format.FormatMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.UUID;

/**
 * Dev check that the jsonb columns mapped as Jackson 3 {@link JsonNode} ({@code AiAction.params},
 * {@code TicketMessage.payload}) survive a write and a fresh read. Everything runs in one rollback-only transaction,
 * so no rows remain.
 */
@Component
@ConditionalOnBooleanProperty("app.ai.dev-endpoints")
public class JsonRoundtripCheck {

    private static final String PARAMS = """
            {"orderId": 42, "reason": "DAMAGED", "items": [{"sku": "A-1", "qty": 2}, {"sku": "B-7", "qty": 1}],
             "flags": {"partial": true, "note": null}, "label": "Größe ✓"}""";
    private static final String PAYLOAD = """
            {"type": "ENVELOPE", "status": "OK", "text": "Here is your order",
             "blocks": [{"kind": "ORDER_DETAIL", "order": {"id": 42, "total": "19.99", "lines": [1, 2, 3]}}],
             "suggestedReplies": [], "meta": {"nested": {"deep": {"ok": true}}}}""";

    @PersistenceContext
    private EntityManager em;
    private final TransactionTemplate tx;

    public JsonRoundtripCheck(PlatformTransactionManager transactionManager) {
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public record Result(boolean passed, String jsonFormatMapper, boolean paramsEqual, boolean payloadEqual,
                         JsonNode paramsWritten, JsonNode paramsRead, JsonNode payloadWritten, JsonNode payloadRead,
                         long rowsRemaining, String error) {
    }

    public Result run(Long userId) {
        JsonMapper mapper = JsonMapper.shared();
        JsonNode params = mapper.readTree(PARAMS);
        JsonNode payload = mapper.readTree(PAYLOAD);
        String marker = "json-roundtrip-" + UUID.randomUUID();
        String formatMapper = formatMapperName();

        JsonNode[] read = new JsonNode[2];
        String error = null;
        try {
            tx.executeWithoutResult(status -> {
                status.setRollbackOnly();
                Ticket ticket = em.createQuery("select t from Ticket t order by t.id", Ticket.class)
                        .setMaxResults(1).getSingleResult();
                Instant now = Instant.now();

                AiAction action = AiAction.open(marker, ticket.getId(), userId, ActionType.values()[0], null, null,
                        params, false, now.plusSeconds(600), now);
                em.persist(action);
                TicketMessage message = newMessage(ticket, marker, payload, now);
                em.persist(message);
                em.flush();
                em.clear();

                read[0] = em.find(AiAction.class, action.getId()).getParams();
                read[1] = em.find(TicketMessage.class, message.getId()).getPayload();
            });
        } catch (Exception e) {
            error = e.getClass().getName() + ": " + e.getMessage();
        }

        long remaining = ((Number) em.createNativeQuery(
                        "select (select count(*) from ai_action where token_hash = ?1)"
                                + " + (select count(*) from ticket_message where body = ?1)")
                .setParameter(1, marker).getSingleResult()).longValue();
        boolean paramsEqual = params.equals(read[0]);
        boolean payloadEqual = payload.equals(read[1]);
        return new Result(error == null && paramsEqual && payloadEqual && remaining == 0, formatMapper,
                paramsEqual, payloadEqual, params, read[0], payload, read[1], remaining, error);
    }

    private String formatMapperName() {
        FormatMapper fm = em.getEntityManagerFactory().unwrap(SessionFactoryImplementor.class)
                .getSessionFactoryOptions().getJsonFormatMapper();
        return fm == null ? null : fm.getClass().getName();
    }

    /** TicketMessage has no factory yet (agent replies are not written so far), so this dev check fills it reflectively. */
    private static TicketMessage newMessage(Ticket ticket, String body, JsonNode payload, Instant now) {
        try {
            Constructor<TicketMessage> ctor = TicketMessage.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            TicketMessage m = ctor.newInstance();
            set(m, "ticket", ticket);
            set(m, "authorType", AuthorType.AI);
            set(m, "body", body);
            set(m, "internal", false);
            set(m, "createdAt", now);
            set(m, "kind", MessageKind.ENVELOPE);
            set(m, "payload", payload);
            set(m, "schemaVersion", 1);
            return m;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot build TicketMessage", e);
        }
    }

    private static void set(Object target, String name, Object value) throws ReflectiveOperationException {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
