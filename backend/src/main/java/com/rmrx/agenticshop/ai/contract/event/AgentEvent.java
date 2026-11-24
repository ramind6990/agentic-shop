package com.rmrx.agenticshop.ai.contract.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.rmrx.agenticshop.ai.contract.block.Block;
import com.rmrx.agenticshop.ai.contract.response.AgentResponse;

import java.time.Instant;
import java.util.UUID;

/**
 * Transient progress events streamed to the client while a run is in flight; never persisted. seq increases by one
 * per event within a run so the client can order them and detect gaps. Serialized with a {@code type} field.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = AgentEvent.RunStarted.class, name = "RUN_STARTED"),
        @JsonSubTypes.Type(value = AgentEvent.TokenDelta.class, name = "TOKEN_DELTA"),
        @JsonSubTypes.Type(value = AgentEvent.ToolStarted.class, name = "TOOL_STARTED"),
        @JsonSubTypes.Type(value = AgentEvent.ToolFinished.class, name = "TOOL_FINISHED"),
        @JsonSubTypes.Type(value = AgentEvent.BlockReady.class, name = "BLOCK_READY"),
        @JsonSubTypes.Type(value = AgentEvent.Final.class, name = "FINAL"),
        @JsonSubTypes.Type(value = AgentEvent.Error.class, name = "ERROR")
})
public sealed interface AgentEvent {

    UUID runId();

    long seq();

    Instant at();

    /** The run began; ticketId is null when no ticket exists yet. */
    record RunStarted(UUID runId, long seq, Instant at, Long ticketId) implements AgentEvent {
    }

    /** A chunk of the reply text as the model writes it. */
    record TokenDelta(UUID runId, long seq, Instant at, String text) implements AgentEvent {
    }

    /** A tool call began; progressLabel is user-facing, e.g. "Looking up your orders…". */
    record ToolStarted(UUID runId, long seq, Instant at, String toolName, String progressLabel)
            implements AgentEvent {
    }

    /** A tool call ended; errorCode is null on success. */
    record ToolFinished(UUID runId, long seq, Instant at, String toolName, boolean success, String errorCode,
                        long durationMs) implements AgentEvent {
    }

    /** A block is ready to render before the final reply. */
    record BlockReady(UUID runId, long seq, Instant at, Block block) implements AgentEvent {
    }

    /** The complete reply; the last event of a successful run. */
    record Final(UUID runId, long seq, Instant at, AgentResponse response) implements AgentEvent {
    }

    /** The run failed; retryable tells the client whether sending again may help. */
    record Error(UUID runId, long seq, Instant at, String code, String message, boolean retryable)
            implements AgentEvent {
    }
}
