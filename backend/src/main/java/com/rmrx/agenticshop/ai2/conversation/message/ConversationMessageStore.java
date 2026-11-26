package com.rmrx.agenticshop.ai2.conversation.message;

import com.rmrx.agenticshop.ai2.contract.interaction.HumanAnswer;
import com.rmrx.agenticshop.ai2.contract.interaction.InteractionJson;
import com.rmrx.agenticshop.ai2.contract.reply.ReplyEnvelope;
import com.rmrx.agenticshop.ai2.graph.agent.pending.PendingInteraction;
import com.rmrx.agenticshop.ai2.graph.agent.response.AgentResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * The chat timeline ({@code ai2_conversation_message}), oldest first.
 */
@Component
public class ConversationMessageStore {

    private static final String COLUMNS = "id, conversation_id, run_id, kind, text, payload, display, reply, "
            + "interaction_id, interaction_status, answer, created_at";

    private final JdbcTemplate jdbc;
    private final RowMapper<ConversationMessage> mapper = (rs, i) -> {
        String status = rs.getString("interaction_status");
        return new ConversationMessage(
                rs.getLong("id"), rs.getLong("conversation_id"), rs.getObject("run_id", UUID.class),
                MessageKind.valueOf(rs.getString("kind")), rs.getString("text"),
                InteractionJson.tree(rs.getString("payload")), InteractionJson.tree(rs.getString("display")),
                InteractionJson.tree(rs.getString("reply")), rs.getObject("interaction_id", UUID.class),
                status == null ? null : InteractionStatus.valueOf(status),
                InteractionJson.tree(rs.getString("answer")), rs.getTimestamp("created_at").toInstant());
    };

    public ConversationMessageStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void appendUser(Long conversationId, UUID runId, String text) {
        insert(conversationId, runId, MessageKind.USER, text, null, null, null, null, null);
    }

    /** The reply: the agent's response and the shown result (for the history), and the envelope the UI renders. */
    public void appendReply(Long conversationId, UUID runId, AgentResponse response, ReplyDisplay display,
                            ReplyEnvelope reply) {
        insert(conversationId, runId, MessageKind.ASSISTANT, response.text(), InteractionJson.write(response),
                display == null ? null : InteractionJson.write(display), InteractionJson.write(reply), null, null);
    }

    /** A question or approval the run paused on, PENDING until answered or abandoned. */
    public void appendInteraction(Long conversationId, UUID runId, PendingInteraction interaction) {
        insert(conversationId, runId, MessageKind.INTERACTION, interaction.promptText(),
                InteractionJson.write(interaction), null, null, interaction.interactionId(),
                InteractionStatus.PENDING);
    }

    /** Ends a PENDING interaction with its status and the answer (null when it was not answered). */
    public void resolveInteraction(UUID interactionId, InteractionStatus status, HumanAnswer answer) {
        jdbc.update("""
                        update ai2_conversation_message
                        set interaction_status = ?, answer = ?::jsonb, updated_at = now()
                        where interaction_id = ? and interaction_status = 'PENDING'""",
                status.name(), answer == null ? null : InteractionJson.write(answer), interactionId);
    }

    /** The whole timeline. */
    public List<ConversationMessage> list(Long conversationId) {
        return jdbc.query("select " + COLUMNS + " from ai2_conversation_message where conversation_id = ? order by id",
                mapper, conversationId);
    }

    /** The messages after {@code afterId} that do not belong to {@code excludedRunId}, oldest first. */
    public List<ConversationMessage> listAfter(Long conversationId, long afterId, UUID excludedRunId) {
        return jdbc.query("select " + COLUMNS + " from ai2_conversation_message "
                        + "where conversation_id = ? and id > ? and run_id is distinct from ? order by id",
                mapper, conversationId, afterId, excludedRunId);
    }

    /** How many messages {@link #listAfter} would return. */
    public int countAfter(Long conversationId, long afterId, UUID excludedRunId) {
        Integer n = jdbc.queryForObject("select count(*) from ai2_conversation_message "
                        + "where conversation_id = ? and id > ? and run_id is distinct from ?",
                Integer.class, conversationId, afterId, excludedRunId);
        return n == null ? 0 : n;
    }

    private void insert(Long conversationId, UUID runId, MessageKind kind, String text, String payload,
                        String display, String reply, UUID interactionId, InteractionStatus status) {
        jdbc.update("""
                        insert into ai2_conversation_message (conversation_id, run_id, kind, text, payload, display,
                                                              reply, interaction_id, interaction_status, created_at,
                                                              updated_at)
                        values (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, now(), now())""",
                conversationId, runId, kind.name(), text, payload, display, reply, interactionId,
                status == null ? null : status.name());
    }
}
