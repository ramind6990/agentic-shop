package com.rmrx.agenticshop.ai2.graph.checkpoint;

import com.rmrx.agenticshop.ai2.graph.state.RunState;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.langchain4j.serializer.std.LC4jStateSerializer;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Checkpoint saver of the run graph.
 * <p>
 * For now every method delegates to an in-memory {@link MemorySaver}: checkpoints do not survive a restart.
 */
// TODO replace the in-memory delegate with JDBC persistence (one thread per run); compile the graph with
//  releaseThread(false) and delete a run's checkpoints when the run ends.
@Component("ai2JdbcCheckpointSaver")
public class JdbcCheckpointSaver implements BaseCheckpointSaver {

    private final MemorySaver delegate = new MemorySaver();
    private final StateSerializer<RunState> serializer = new LC4jStateSerializer<>(RunState::new);

    /** The serializer the graph uses for its state (and the JDBC implementation will use for its rows). */
    public StateSerializer<RunState> serializer() {
        return serializer;
    }

    @Override
    public Collection<Checkpoint> list(RunnableConfig config) {
        return delegate.list(config);
    }

    @Override
    public Optional<Checkpoint> get(RunnableConfig config) {
        return delegate.get(config);
    }

    @Override
    public RunnableConfig put(RunnableConfig config, Checkpoint checkpoint) throws Exception {
        return delegate.put(config, checkpoint);
    }

    @Override
    public Tag release(RunnableConfig config) throws Exception {
        return delegate.release(config);
    }

    @Override
    public Tag release(RunnableConfig config, String message) throws Exception {
        return delegate.release(config, message);
    }

    @Override
    public Tag releaseOnError(RunnableConfig config, Throwable error) throws Exception {
        return delegate.releaseOnError(config, error);
    }

    @Override
    public <S extends AgentState> CompletableFuture<InterruptionMetadata<S>> registerInterruption(
            RunnableConfig config, InterruptionMetadata<S> metadata) {
        return delegate.registerInterruption(config, metadata);
    }

    @Override
    public Optional<Tag> tag(RunnableConfig config, Integer version) throws Exception {
        return delegate.tag(config, version);
    }

    @Override
    public void putSubGraphSaver(RunnableConfig parentConfig, RunnableConfig subGraphConfig,
                                 BaseCheckpointSaver subGraphSaver) {
        delegate.putSubGraphSaver(parentConfig, subGraphConfig, subGraphSaver);
    }

    @Override
    public Collection<SubGraphSaver> listSubGraphSaver(RunnableConfig parentConfig) {
        return delegate.listSubGraphSaver(parentConfig);
    }
}
