package com.rmrx.agenticshop.ai2.events;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.rmrx.agenticshop.ai2.contract.tool.ToolResult;
import com.rmrx.agenticshop.ai2.graph.agent.pending.PendingInteraction;
import com.rmrx.agenticshop.ai2.graph.agent.response.AgentResponse;

import java.time.Instant;
import java.util.UUID;

/**
 * Transient progress events streamed to the client while a run executes; never persisted. seq increases by one per
 * event within a run (also across a pause and its resume) so the client can order them and detect gaps. Serialized
 * with a {@code type} field.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = AgentEvent.RunStarted.class, name = "RUN_STARTED"),
        @JsonSubTypes.Type(value = AgentEvent.ToolStarted.class, name = "TOOL_STARTED"),
        @JsonSubTypes.Type(value = AgentEvent.ToolFinished.class, name = "TOOL_FINISHED"),
        @JsonSubTypes.Type(value = AgentEvent.Final.class, name = "FINAL"),
        @JsonSubTypes.Type(value = AgentEvent.Paused.class, name = "PAUSED"),
        @JsonSubTypes.Type(value = AgentEvent.Error.class, name = "ERROR")
})
public sealed interface AgentEvent {

    UUID runId();

    long seq();

    Instant at();

    /** The run began, or resumed after the user's answer. */
    record RunStarted(UUID runId, long seq, Instant at, Long conversationId) implements AgentEvent {
    }

    /** A tool call began; progressLabel is user-facing, e.g. "Searching products for running shoes". */
    record ToolStarted(UUID runId, long seq, Instant at, String toolName, String progressLabel)
            implements AgentEvent {
    }

    /** A tool call ended; errorCode is null on success. */
    record ToolFinished(UUID runId, long seq, Instant at, String toolName, boolean success, String errorCode,
                        long durationMs) implements AgentEvent {
    }

    /** The reply, with the tool result it shows (or null); the last event of a completed run. */
    record Final(UUID runId, long seq, Instant at, AgentResponse response, ToolResult displayed)
            implements AgentEvent {
    }

    /** The run waits for the user's answer to {@code interaction}; the last event until the run is resumed. */
    record Paused(UUID runId, long seq, Instant at, PendingInteraction interaction) implements AgentEvent {
    }

    /** The run failed; retryable tells the client whether sending again may help. */
    record Error(UUID runId, long seq, Instant at, String code, String message, boolean retryable)
            implements AgentEvent {
    }
}
