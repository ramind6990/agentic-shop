package com.rmrx.agenticshop.ai2.tools;

import com.rmrx.agenticshop.ai2.events.AgentEventPublisher;
import com.rmrx.agenticshop.ai2.events.RunEvents;
import com.rmrx.agenticshop.ai2.graph.state.RunContext;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Emits TOOL_STARTED (with the call's rendered {@link ToolLabel}) and TOOL_FINISHED (outcome and duration) around one
 * tool execution. Nothing is emitted when the run has no open event stream; a label that cannot be rendered falls
 * back to a generic one, so progress never affects the call.
 */
@Component
public class ToolProgress {

    static final String DEFAULT_LABEL = "Working on it…";

    private final ToolRegistry registry;
    private final RunEvents events;

    public ToolProgress(ToolRegistry registry, RunEvents events) {
        this.registry = registry;
        this.events = events;
    }

    public ExecutedCall track(ToolExecutionRequest call, RunContext context, Supplier<ExecutedCall> execution) {
        Optional<AgentEventPublisher> publisher = events.publisher(context.runId());
        publisher.ifPresent(p -> p.toolStarted(call.name(), label(call, context)));
        long start = System.nanoTime();
        ExecutedCall executed = execution.get();
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        boolean success = executed.result() != null;
        publisher.ifPresent(p -> p.toolFinished(call.name(), success, durationMs));
        return executed;
    }

    private String label(ToolExecutionRequest call, RunContext context) {
        try {
            return registry.catalog(context).label(call).orElse(DEFAULT_LABEL);
        } catch (RuntimeException e) {
            return DEFAULT_LABEL;
        }
    }
}
