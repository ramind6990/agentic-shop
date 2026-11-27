package com.rmrx.agenticshop.ai2.tools;

import com.rmrx.agenticshop.ai2.events.AgentEventPublisher;
import com.rmrx.agenticshop.ai2.events.RunEvents;
import com.rmrx.agenticshop.ai2.graph.state.RunContext;
import com.rmrx.agenticshop.ai2.trace.TraceRecorder;
import com.rmrx.agenticshop.ai2.trace.TraceStep;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Wraps one tool execution: emits TOOL_STARTED (with the call's rendered {@link ToolLabel}) and TOOL_FINISHED (outcome
 * and duration), and writes the call into the run's trace (arguments, the result the model got, latency). Nothing is
 * emitted when the run has no open event stream; a label that cannot be rendered falls back to a generic one, so
 * tracking never affects the call.
 */
@Component
public class ToolCallTracker {

    static final String DEFAULT_LABEL = "Working on it…";
    static final String TOOL_ERROR = "TOOL_ERROR";

    private final ToolRegistry registry;
    private final RunEvents events;
    private final TraceRecorder trace;

    public ToolCallTracker(ToolRegistry registry, RunEvents events, TraceRecorder trace) {
        this.registry = registry;
        this.events = events;
        this.trace = trace;
    }

    public ExecutedCall track(ToolExecutionRequest call, RunContext context, Supplier<ExecutedCall> execution) {
        Optional<AgentEventPublisher> publisher = events.publisher(context.runId());
        publisher.ifPresent(p -> p.toolStarted(call.name(), label(call, context)));
        long start = System.nanoTime();
        ExecutedCall executed = execution.get();
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        boolean success = executed.result() != null;
        publisher.ifPresent(p -> p.toolFinished(call.name(), success, durationMs));
        trace.step(context.runId(), TraceStep.tool(call.name(), call.arguments(), executed.message().text(),
                success ? null : TOOL_ERROR, durationMs));
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
