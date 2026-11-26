package com.rmrx.agenticshop.ai2.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rmrx.agenticshop.ai2.contract.reply.ReplyEnvelope;
import com.rmrx.agenticshop.ai2.graph.agent.pending.PendingInteraction;

import java.time.Instant;
import java.util.UUID;

/**
 * The one message the UI receives (WebSocket frame, SSE event): {@link #type()} says what it is, and exactly the
 * matching payload is set — {@code reply} (the reply envelope), {@code interaction} (a question or approval to answer),
 * {@code progress} (a progress notice) or {@code error}.
 * <p>
 * Run events carry {@code seq} (1, 2, 3 … per run, continuing across a pause and its resume); acknowledgements and
 * refusals of a client request carry its {@code requestId} instead. Never persisted.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatResponse(Type type, String requestId, Long conversationId, UUID runId, Long seq, Instant at,
                           ReplyEnvelope reply, PendingInteraction interaction, Progress progress, Error error) {

    public enum Type {
        REPLY,
        INTERACTION,
        PROGRESS,
        ERROR
    }

    /**
     * A progress notice. label is user-facing (the tool's rendered {@code @ToolLabel}); toolName, success and
     * durationMs are set for tool stages.
     */
    public record Progress(Stage stage, String label, String toolName, Boolean success, Long durationMs) {
    }

    public enum Stage {
        /** The client's chat request was taken; the run's events follow. */
        ACCEPTED,
        /** The run started, or resumed after the user's answer. */
        RUN_STARTED,
        TOOL_STARTED,
        TOOL_FINISHED
    }

    /** retryable tells the client whether sending again may help. */
    public record Error(ErrorCode code, String message, boolean retryable) {
    }

    public enum ErrorCode {
        BAD_REQUEST,
        NOT_FOUND,
        CONFLICT,
        RUN_NOT_LIVE,
        /** The answered question or approval is no longer open (the run was abandoned); send a new message. */
        INTERACTION_CLOSED,
        AGENT_ERROR,
        INTERNAL
    }

    /** Whether this ends a run's stream: everything but a progress notice. */
    public boolean last() {
        return type != Type.PROGRESS;
    }

    // ------------------------------------------------------------------ run events

    public static ChatResponse progress(UUID runId, Long conversationId, long seq, Progress progress) {
        return new ChatResponse(Type.PROGRESS, null, conversationId, runId, seq, Instant.now(), null, null, progress,
                null);
    }

    public static ChatResponse reply(UUID runId, Long conversationId, long seq, ReplyEnvelope reply) {
        return new ChatResponse(Type.REPLY, null, conversationId, runId, seq, Instant.now(), reply, null, null, null);
    }

    public static ChatResponse interaction(UUID runId, Long conversationId, long seq, PendingInteraction interaction) {
        return new ChatResponse(Type.INTERACTION, null, conversationId, runId, seq, Instant.now(), null, interaction,
                null, null);
    }

    public static ChatResponse error(UUID runId, Long conversationId, Long seq, Error error) {
        return new ChatResponse(Type.ERROR, null, conversationId, runId, seq, Instant.now(), null, null, null, error);
    }

    // ------------------------------------------------------------------ answers to a client request

    public static ChatResponse accepted(String requestId, Long conversationId, UUID runId) {
        return new ChatResponse(Type.PROGRESS, requestId, conversationId, runId, null, Instant.now(), null, null,
                new Progress(Stage.ACCEPTED, null, null, null, null), null);
    }

    public static ChatResponse refused(String requestId, Long conversationId, UUID runId, ErrorCode code,
                                       String message, boolean retryable) {
        return new ChatResponse(Type.ERROR, requestId, conversationId, runId, null, Instant.now(), null, null, null,
                new Error(code, message, retryable));
    }
}
