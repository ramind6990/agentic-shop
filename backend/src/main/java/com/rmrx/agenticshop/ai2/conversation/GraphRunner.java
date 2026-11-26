package com.rmrx.agenticshop.ai2.conversation;

import com.rmrx.agenticshop.ai2.contract.interaction.HumanAnswer;
import com.rmrx.agenticshop.ai2.graph.RunGraph;
import com.rmrx.agenticshop.ai2.graph.agent.pending.PendingInteraction;
import com.rmrx.agenticshop.ai2.graph.checkpoint.JdbcCheckpointSaver;
import com.rmrx.agenticshop.ai2.graph.state.RunContext;
import com.rmrx.agenticshop.ai2.graph.state.RunState;
import com.rmrx.agenticshop.ai2.tools.ToolCallExecutor;
import com.rmrx.agenticshop.ai2.tools.ToolRegistry;
import dev.langchain4j.data.message.ChatMessage;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.GraphResult;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Starts, resumes and discards graph runs; the only place outside the graph package that talks to langgraph4j. A run's
 * checkpoint thread is its run id. Runs execute synchronously in the calling thread.
 */
// TODO stream node outputs as progress events instead of waiting for the end of the run.
@Component
public class GraphRunner {

    private static final Logger log = LoggerFactory.getLogger(GraphRunner.class);

    private final CompiledGraph<RunState> graph;
    private final JdbcCheckpointSaver checkpoints;

    public GraphRunner(ToolRegistry registry, ToolCallExecutor executor, JdbcCheckpointSaver checkpoints)
            throws GraphStateException {
        this.graph = new RunGraph(checkpoints, registry, executor).graph();
        this.checkpoints = checkpoints;
    }

    /** Runs a new graph with the run's first messages (the user's message) until it completes or pauses. */
    public RunOutcome start(RunContext context, List<ChatMessage> messages) {
        Map<String, Object> input = Map.of(
                RunState.CONTEXT, context,
                RunState.MESSAGES, new ArrayList<>(messages));
        return run(GraphInput.args(input), config(context.runId()), context.runId());
    }

    /** Stores the answer to the paused call and continues the run until it completes or pauses again. */
    public RunOutcome resume(UUID runId, String callId, HumanAnswer answer) {
        try {
            RunnableConfig config = graph.updateState(config(runId),
                    Map.of(RunState.HUMAN_ANSWERS, Map.of(callId, answer)));
            return run(GraphInput.resume(), config, runId);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("could not store the answer of run " + runId, e);
        }
    }

    /** Drops a run's checkpoints (an abandoned or failed run). */
    public void discard(UUID runId) {
        try {
            checkpoints.release(config(runId));
        } catch (Exception e) {
            log.warn("could not release the checkpoints of run {}", runId, e);
        }
    }

    private RunOutcome run(GraphInput input, RunnableConfig config, UUID runId) {
        GraphResult result = graph.invokeSync(input, config);
        if (result.isInterruptionMetadata()) {
            InterruptionMetadata<RunState> interruption = result.asInterruptionMetadata();
            PendingInteraction pending = interruption.metadata(PendingInteraction.METADATA_KEY)
                    .map(PendingInteraction.class::cast)
                    .orElseThrow(() -> new IllegalStateException(
                            "run " + runId + " paused at " + interruption.nodeId() + " without a pending interaction"));
            return new RunOutcome.Paused(runId, pending);
        }
        RunState finalState = new RunState(finalStateData(result));
        return new RunOutcome.Completed(runId, finalState.response()
                .orElseThrow(() -> new IllegalStateException("run " + runId + " ended without a response")));
    }

    /** The completed run's state: returned as data, or (the checkpoints being released at the end) as the last one. */
    private static Map<String, Object> finalStateData(GraphResult result) {
        if (result.isStateData()) return result.asStateData();
        if (result.isCheckpointSaverTag()) return result.asLastCheckpointStateData();
        throw new IllegalStateException("unexpected graph result: " + result.type());
    }

    private static RunnableConfig config(UUID runId) {
        return RunnableConfig.builder().threadId(runId.toString()).build();
    }
}
