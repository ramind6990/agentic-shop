package com.rmrx.agenticshop.ai2.checkpointer;

import com.rmrx.agenticshop.ai2.graph.state.RunState;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.bsc.langgraph4j.langchain4j.serializer.std.LC4jStateSerializer;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The ai2 graph's checkpoints in Postgres ({@code ai2_checkpoint}). The langgraph4j thread id is the run id; a run's
 * checkpoints are deleted with its active run row ({@code ai2_active_run}, cascade), so a paused run survives a
 * restart and finished runs leave nothing behind.
 * <p>
 * The state is written with the graph's own serializer ({@link LC4jStateSerializer}: Java object stream with
 * LangChain4j message support), so every state value must be {@link java.io.Serializable}.
 * <p>
 * Unlike a best-effort cache, a failed write fails the run: pausing and resuming depend on the checkpoints.
 */
@Component("ai2JdbcCheckpointSaver")
public class JdbcCheckpointSaver implements BaseCheckpointSaver {

    private static final Logger log = LoggerFactory.getLogger(JdbcCheckpointSaver.class);
    private static final String COLUMNS = "id, node_id, next_node_id, state_data, created_at";

    private final JdbcTemplate jdbc;
    private final StateSerializer<RunState> serializer = new LC4jStateSerializer<>(RunState::new);
    private final RowMapper<Checkpoint> mapper = (rs, i) -> {
        try {
            RunState state = serializer.readDataFromString(rs.getString("state_data"));
            return Checkpoint.builder()
                    .id(rs.getString("id"))
                    .nodeId(rs.getString("node_id"))
                    .nextNodeId(rs.getString("next_node_id"))
                    .state(state.data())
                    .savedAt(rs.getTimestamp("created_at").toInstant())
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("unreadable ai2 checkpoint " + rs.getString("id"), e);
        }
    };

    public JdbcCheckpointSaver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The serializer the graph must use too, so stored states read back as {@link RunState}. */
    public StateSerializer<RunState> serializer() {
        return serializer;
    }

    // ------------------------------------------------------------------ BaseCheckpointSaver

    /** Newest first. */
    @Override
    public Collection<Checkpoint> list(RunnableConfig config) {
        UUID run = runId(config);
        return jdbc.query("select " + COLUMNS + " from ai2_checkpoint where run_id = ? order by seq desc", mapper, run);
    }

    @Override
    public Optional<Checkpoint> get(RunnableConfig config) {
        UUID run = runId(config);
        if (config.checkPointId().isPresent()) {
            return jdbc.query("select " + COLUMNS + " from ai2_checkpoint where run_id = ? and id = ?", mapper, run,
                    config.checkPointId().get()).stream().findFirst();
        }
        return jdbc.query("select " + COLUMNS + " from ai2_checkpoint where run_id = ? order by seq desc limit 1",
                mapper, run).stream().findFirst();
    }

    @Override
    public RunnableConfig put(RunnableConfig config, Checkpoint checkpoint) throws Exception {
        UUID run = runId(config);
        String data = serializer.writeDataAsString(checkpoint.getState());
        Timestamp at = Timestamp.from(checkpoint.hasSavedAt() ? checkpoint.savedAt() : Instant.now());
        if (config.checkPointId().isPresent()) {
            // an explicit update of an existing checkpoint (e.g. CompiledGraph.updateState with a checkpoint id)
            jdbc.update("update ai2_checkpoint set id = ?, node_id = ?, next_node_id = ?, content_type = ?, "
                            + "state_data = ?, created_at = ? where run_id = ? and id = ?",
                    checkpoint.getId(), checkpoint.getNodeId(), checkpoint.getNextNodeId(), serializer.contentType(),
                    data, at, run, config.checkPointId().get());
        } else {
            // the conversation comes from the run's active run row (which must exist while the run executes)
            int inserted = jdbc.update("""
                            insert into ai2_checkpoint (id, run_id, conversation_id, node_id, next_node_id, content_type,
                                                        state_data, created_at)
                            select ?, a.run_id, a.conversation_id, ?, ?, ?, ?, ?
                            from ai2_active_run a where a.run_id = ?""",
                    checkpoint.getId(), checkpoint.getNodeId(), checkpoint.getNextNodeId(), serializer.contentType(),
                    data, at, run);
            if (inserted == 0) throw new IllegalStateException("no active run " + run + " for its checkpoint");
        }
        return config.withCheckPointId(checkpoint.getId());
    }

    @Override
    public Tag release(RunnableConfig config) {
        return release(config, null);
    }

    /** Deletes the run's checkpoints; langgraph4j expects them back in the tag. */
    @Override
    public Tag release(RunnableConfig config, String message) {
        Collection<Checkpoint> released = list(config);
        int deleted = jdbc.update("delete from ai2_checkpoint where run_id = ?", runId(config));
        log.debug("released {} checkpoints of run {}", deleted, threadId(config));
        return new Tag(threadId(config), released);
    }

    @Override
    public Tag releaseOnError(RunnableConfig config, Throwable error) {
        return release(config, error == null ? null : error.getMessage());
    }

    @Override
    public <S extends AgentState> CompletableFuture<InterruptionMetadata<S>> registerInterruption(
            RunnableConfig config, InterruptionMetadata<S> metadata) {
        // the pause itself is already a checkpoint; the pending interaction is kept on the active run
        return CompletableFuture.completedFuture(metadata);
    }

    @Override
    public Optional<Tag> tag(RunnableConfig config, Integer version) {
        return Optional.empty();
    }

    @Override
    public void putSubGraphSaver(RunnableConfig parentConfig, RunnableConfig subGraphConfig,
                                 BaseCheckpointSaver subGraphSaver) {
        // the run graph has no subgraphs
    }

    @Override
    public Collection<SubGraphSaver> listSubGraphSaver(RunnableConfig parentConfig) {
        return List.of();
    }

    // ------------------------------------------------------------------ run-level API

    /** Checkpoints stored for a run. */
    public int count(UUID runId) {
        Integer n = jdbc.queryForObject("select count(*) from ai2_checkpoint where run_id = ?", Integer.class, runId);
        return n == null ? 0 : n;
    }

    private UUID runId(RunnableConfig config) {
        String thread = threadId(config);
        try {
            return UUID.fromString(thread);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("ai2 checkpoint thread must be a run id, got: " + thread, e);
        }
    }
}
