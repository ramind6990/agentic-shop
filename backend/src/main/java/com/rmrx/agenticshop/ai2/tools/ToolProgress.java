package com.rmrx.agenticshop.ai2.tools;

import com.rmrx.agenticshop.ai2.events.AgentEventPublisher;
import com.rmrx.agenticshop.ai2.events.RunEvents;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Emits TOOL_STARTED (with the call's rendered {@link ToolLabel}) and TOOL_FINISHED (outcome and duration) around one
 * tool execution. Nothing is emitted when the run has no open event stream; a label that cannot be rendered falls
 * back to a generic one, so progress never affects the call.
 */
@Component
public class ToolProgress {

    static final String DEFAULT_LABEL = "Working on it…";
    static final String TOOL_FAILED = "TOOL_FAILED";

    private final ToolRegistry registry;
    private final RunEvents events;

    public ToolProgress(ToolRegistry registry, RunEvents events) {
        this.registry = registry;
        this.events = events;
    }

    public ExecutedCall track(ToolExecutionRequest call, UUID runId, Supplier<ExecutedCall> execution) {
        Optional<AgentEventPublisher> publisher = events.publisher(runId);
        publisher.ifPresent(p -> p.toolStarted(call.name(), label(call)));
        long start = System.nanoTime();
        ExecutedCall executed = execution.get();
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        boolean success = executed.result() != null;
        publisher.ifPresent(p -> p.toolFinished(call.name(), success, success ? null : TOOL_FAILED, durationMs));
        return executed;
    }

    private String label(ToolExecutionRequest call) {
        try {
            return registry.label(call).orElse(DEFAULT_LABEL);
        } catch (RuntimeException e) {
            return DEFAULT_LABEL;
        }
    }
}
