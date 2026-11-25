package com.rmrx.agenticshop.ai.graph;

import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.bsc.langgraph4j.langchain4j.serializer.std.LC4jStateSerializer;
import org.bsc.langgraph4j.serializer.StateSerializer;
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
 * LangGraph4j checkpoint saver on the Flyway-managed {@code ai_checkpoint} table. The thread id is the run id: one
 * thread per turn, since a turn's graph state starts from the ticket timeline (conversation memory lives in
 * {@code ticket_message}, not in graph state); that ties a checkpoint's lifetime to its {@code ai_run} row (FK,
 * cascade) and makes "is this run resumable" a lookup by run id.
 *
 * <p>The state is written with the graph's own {@link LC4jStateSerializer} (Java object stream with LangChain4j
 * message support), so only the serializable {@link AgentState} is stored; the per-turn {@link TurnRuntime} travels
 * in the RunnableConfig metadata and is never persisted.
 *
 * <p>Checkpoints are best effort: a failed write is logged and the turn goes on (it only loses resumability). No
 * in-memory cache: the graph only puts during a run, and reads (latest checkpoint) happen at resume time.
 * {@code release} deletes the thread; the runner deletes a run's checkpoints itself when the run ends.
 */
@Slf4j
@Component
public class JdbcCheckpointSaver implements BaseCheckpointSaver {

    private static final String COLUMNS = "id, node_id, next_node_id, content_type, state_data, created_at";

    private final JdbcTemplate jdbc;
    private final StateSerializer<AgentState> serializer = new LC4jStateSerializer<>(AgentState::new);
    private final RowMapper<Checkpoint> mapper = (rs, i) -> {
        try {
            AgentState state = serializer.readDataFromString(rs.getString("state_data"));
            return Checkpoint.builder()
                    .id(rs.getString("id"))
                    .nodeId(rs.getString("node_id"))
                    .nextNodeId(rs.getString("next_node_id"))
                    .state(state.data())
                    .savedAt(rs.getTimestamp("created_at").toInstant())
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("unreadable checkpoint " + rs.getString("id"), e);
        }
    };

    public JdbcCheckpointSaver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The serializer the graph must use too, so stored states read back as {@link AgentState}. */
    public StateSerializer<AgentState> serializer() {
        return serializer;
    }

    // ------------------------------------------------------------------ BaseCheckpointSaver

    @Override
    public Collection<Checkpoint> list(RunnableConfig config) {
        UUID run = runId(config);
        if (run == null) return List.of();
        return jdbc.query("select " + COLUMNS + " from ai_checkpoint where run_id = ? order by seq desc", mapper,
                run);
    }

    @Override
    public Optional<Checkpoint> get(RunnableConfig config) {
        UUID run = runId(config);
        if (run == null) return Optional.empty();
        if (config.checkPointId().isPresent()) {
            return jdbc.query("select " + COLUMNS + " from ai_checkpoint where run_id = ? and id = ?", mapper, run,
                    config.checkPointId().get()).stream().findFirst();
        }
        return latest(run);
    }

    @Override
    public RunnableConfig put(RunnableConfig config, Checkpoint checkpoint) {
        UUID run = runId(config);
        if (run == null) return config;
        try {
            String data = serializer.writeDataAsString(checkpoint.getState());
            Timestamp at = Timestamp.from(checkpoint.hasSavedAt() ? checkpoint.savedAt() : Instant.now());
            if (config.checkPointId().isPresent()) {
                // an explicit update of an existing checkpoint (e.g. CompiledGraph.updateState)
                jdbc.update("update ai_checkpoint set id = ?, node_id = ?, next_node_id = ?, content_type = ?, "
                                + "state_data = ?, created_at = ? where run_id = ? and id = ?",
                        checkpoint.getId(), checkpoint.getNodeId(), checkpoint.getNextNodeId(),
                        serializer.contentType(), data, at, run, config.checkPointId().get());
            } else {
                jdbc.update("insert into ai_checkpoint (id, run_id, node_id, next_node_id, content_type, state_data, "
                                + "created_at) values (?, ?, ?, ?, ?, ?, ?)",
                        checkpoint.getId(), run, checkpoint.getNodeId(), checkpoint.getNextNodeId(),
                        serializer.contentType(), data, at);
            }
        } catch (Exception e) {
            log.warn("Checkpoint {} of run {} (after {}) not saved: {}", checkpoint.getId(), run,
                    checkpoint.getNodeId(), e.toString());
        }
        return config.withCheckPointId(checkpoint.getId());
    }

    @Override
    public Tag release(RunnableConfig config) {
        return release(config, null);
    }

    @Override
    public Tag release(RunnableConfig config, String message) {
        // LangGraph4j expects the released checkpoints in the tag
        String thread = threadId(config);
        UUID run = runId(config);
        if (run == null) return new Tag(thread, List.of());
        Collection<Checkpoint> released = list(config);
        deleteRun(run);
        return new Tag(thread, released);
    }

    @Override
    public Tag releaseOnError(RunnableConfig config, Throwable error) {
        return release(config, error == null ? null : error.getMessage());
    }

    @Override
    public <S extends org.bsc.langgraph4j.state.AgentState> CompletableFuture<InterruptionMetadata<S>>
    registerInterruption(RunnableConfig config, InterruptionMetadata<S> metadata) {
        return CompletableFuture.completedFuture(metadata);
    }

    @Override
    public Optional<Tag> tag(RunnableConfig config, Integer version) {
        return Optional.empty();
    }

    @Override
    public void putSubGraphSaver(RunnableConfig parentConfig, RunnableConfig subGraphConfig,
                                 BaseCheckpointSaver subGraphSaver) {
        // the support graph has no subgraphs
    }

    @Override
    public Collection<SubGraphSaver> listSubGraphSaver(RunnableConfig parentConfig) {
        return List.of();
    }

    // ------------------------------------------------------------------ run-level API

    /** The newest checkpoint of a run. */
    public Optional<Checkpoint> latest(UUID runId) {
        return jdbc.query("select " + COLUMNS + " from ai_checkpoint where run_id = ? order by seq desc limit 1",
                mapper, runId).stream().findFirst();
    }

    public int count(UUID runId) {
        Integer n = jdbc.queryForObject("select count(*) from ai_checkpoint where run_id = ?", Integer.class, runId);
        return n == null ? 0 : n;
    }

    /** Deletes a run's checkpoints (retention: a finished run is never resumed). Never throws. */
    public int deleteRun(UUID runId) {
        try {
            return jdbc.update("delete from ai_checkpoint where run_id = ?", runId);
        } catch (Exception e) {
            log.warn("Checkpoints of run {} not deleted: {}", runId, e.toString());
            return 0;
        }
    }

    /** Deletes checkpoints left behind by runs that are no longer RUNNING (e.g. a delete that failed). */
    public int deleteOfFinishedRuns() {
        return jdbc.update("delete from ai_checkpoint c using ai_run r where r.id = c.run_id and r.status <> 'RUNNING'");
    }

    private UUID runId(RunnableConfig config) {
        String thread = threadId(config);
        try {
            return UUID.fromString(thread);
        } catch (IllegalArgumentException e) {
            log.debug("Thread {} is not a run id; no checkpoints", thread);
            return null;
        }
    }
}
