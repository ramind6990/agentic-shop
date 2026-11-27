package com.rmrx.agenticshop.ai2.activerun;

import com.rmrx.agenticshop.ai2.contract.interaction.InteractionJson;
import com.rmrx.agenticshop.ai2.graph.agent.pending.PendingInteraction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The active run of each conversation, in Postgres ({@code ai2_active_run}): at most one per conversation, which makes
 * the row the conversation's lock. Every change is a single atomic statement keyed by the conversation, so concurrent
 * requests (a second tab, a double-clicked answer, another instance) cannot both win. A paused run, with the question
 * it waits for, survives a restart; deleting the row also deletes the run's checkpoints (cascade).
 */
@Component
public class ActiveRunRegistry {

    private static final Logger log = LoggerFactory.getLogger(ActiveRunRegistry.class);
    private static final String COLUMNS = "conversation_id, run_id, user_id, status, pending_interaction";

    private final JdbcTemplate jdbc;
    private final RowMapper<ActiveRun> mapper = (rs, i) -> {
        String pending = rs.getString("pending_interaction");
        return new ActiveRun(
                rs.getLong("conversation_id"),
                rs.getObject("run_id", UUID.class),
                rs.getLong("user_id"),
                ActiveRun.Status.valueOf(rs.getString("status")),
                pending == null ? null : InteractionJson.read(pending, PendingInteraction.class));
    };

    public ActiveRunRegistry(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ActiveRun> find(Long conversationId) {
        return first(jdbc.query("select " + COLUMNS + " from ai2_active_run where conversation_id = ?", mapper,
                conversationId));
    }

    /** Starts a RUNNING run, unless the conversation already has one. */
    public Optional<ActiveRun> tryStart(Long conversationId, Long userId) {
        UUID runId = UUID.randomUUID();
        int inserted = jdbc.update("""
                insert into ai2_active_run (conversation_id, run_id, user_id, status, started_at, updated_at)
                values (?, ?, ?, 'RUNNING', now(), now())
                on conflict (conversation_id) do nothing""", conversationId, runId, userId);
        return inserted == 1
                ? Optional.of(new ActiveRun(conversationId, runId, userId, ActiveRun.Status.RUNNING, null))
                : Optional.empty();
    }

    /** The run paused on {@code interaction}; ignored when the conversation's run is no longer {@code runId}. */
    public void markPaused(Long conversationId, UUID runId, PendingInteraction interaction) {
        jdbc.update("""
                        update ai2_active_run
                        set status = 'PAUSED', pending_interaction_id = ?, pending_interaction = ?::jsonb,
                            updated_at = now()
                        where conversation_id = ? and run_id = ?""",
                interaction.interactionId(), InteractionJson.write(interaction), conversationId, runId);
    }

    /**
     * PAUSED → RUNNING, only when the run waits for {@code interactionId}. Returns the run as it was while paused
     * (with its pending interaction); empty when another request got there first.
     */
    public Optional<ActiveRun> tryResume(Long conversationId, UUID interactionId) {
        return first(jdbc.query("""
                        with paused as (
                            select conversation_id, run_id, user_id, status, pending_interaction
                            from ai2_active_run
                            where conversation_id = ? and status = 'PAUSED' and pending_interaction_id = ?
                            for update)
                        update ai2_active_run a
                        set status = 'RUNNING', pending_interaction_id = null, pending_interaction = null,
                            updated_at = now()
                        from paused
                        where a.conversation_id = paused.conversation_id
                        returning paused.conversation_id, paused.run_id, paused.user_id, paused.status,
                                  paused.pending_interaction""",
                mapper, conversationId, interactionId));
    }

    /** Removes the conversation's run if it is PAUSED (to abandon it), with its checkpoints; empty otherwise. */
    public Optional<ActiveRun> takePaused(Long conversationId) {
        return first(jdbc.query("delete from ai2_active_run where conversation_id = ? and status = 'PAUSED' "
                + "returning " + COLUMNS, mapper, conversationId));
    }

    /** Ends the conversation's run if it is still {@code runId}, with its checkpoints. */
    public void release(Long conversationId, UUID runId) {
        jdbc.update("delete from ai2_active_run where conversation_id = ? and run_id = ?", conversationId, runId);
    }

    /**
     * Removes every RUNNING run (with its checkpoints) and returns them. Called at startup: such runs were cut off by
     * the previous shutdown and will never finish. PAUSED runs stay; they can still be answered.
     */
    // TODO with more than one instance this must only drop runs of a dead instance (owner + heartbeat), or resume
    //  them from their checkpoints.
    public List<ActiveRun> dropInterrupted() {
        List<ActiveRun> dropped = jdbc.query("delete from ai2_active_run where status = 'RUNNING' returning "
                + COLUMNS, mapper);
        if (!dropped.isEmpty()) log.warn("ai2: dropped {} run(s) interrupted by the last shutdown", dropped.size());
        return dropped;
    }

    private static Optional<ActiveRun> first(List<ActiveRun> runs) {
        return runs.stream().findFirst();
    }
}
