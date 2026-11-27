package com.rmrx.agenticshop.ai.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rmrx.agenticshop.ai.contract.interaction.HumanAnswer;
import com.rmrx.agenticshop.ai.contract.reply.ReplyEnvelope;
import com.rmrx.agenticshop.ai.graph.agent.pending.PendingInteraction;

import java.time.Instant;
import java.util.UUID;

/**
 * The one message the UI receives (WebSocket frame, SSE event): {@link #type()} says what it is, and exactly the
 * matching payload is set — {@code input} (what the user sent, which started or resumed the run), {@code progress}
 * (a progress notice), {@code reply} (the reply envelope), {@code interaction} (a question or approval to answer) or
 * {@code error}.
 * <p>
 * Run events carry {@code seq} (1, 2, 3 … per run, continuing across a pause and its resume). An INPUT carries the
 * {@code requestId} of the request that sent it (only meaningful to that client); a refusal carries the refused
 * request's requestId and no seq. Never persisted.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatResponse(Type type, String requestId, Long conversationId, UUID runId, Long seq, Instant at,
                           Input input, Progress progress, ReplyEnvelope reply, PendingInteraction interaction,
                           Error error) {

    public enum Type {
        INPUT,
        PROGRESS,
        REPLY,
        INTERACTION,
        ERROR
    }

    /**
     * What the user sent: a message ({@code text}), or the answer to a question or approval ({@code answered} + its
     * {@code answer}). The first event of a run, and of its resume.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Input(String text, PendingInteraction answered, HumanAnswer answer) {

        public static Input text(String text) {
            return new Input(text, null, null);
        }

        public static Input answer(PendingInteraction answered, HumanAnswer answer) {
            return new Input(null, answered, answer);
        }
    }

    /**
     * A progress notice: label is user-facing (the tool's rendered {@code @ToolLabel}, or the notice text); success and
     * durationMs are set once the tool finished.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Progress(Stage stage, String label, String toolName, Boolean success, Long durationMs) {
    }

    public enum Stage {
        TOOL_STARTED,
        TOOL_FINISHED,
        /** A model call failed and is retried; label: "Request failed. Retrying (2/5)". */
        RETRYING,
        /** The model works again after a retry; label: "Thinking…". */
        THINKING
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
        /** The user stopped the run. */
        CANCELLED,
        AGENT_ERROR,
        INTERNAL
    }

    /** Whether this ends a run's stream: a reply, a question or an error. */
    public boolean last() {
        return type == Type.REPLY || type == Type.INTERACTION || type == Type.ERROR;
    }

    // ------------------------------------------------------------------ run events

    public static ChatResponse input(UUID runId, Long conversationId, long seq, String requestId, Input input) {
        return new ChatResponse(Type.INPUT, requestId, conversationId, runId, seq, Instant.now(), input, null, null,
                null, null);
    }

    public static ChatResponse progress(UUID runId, Long conversationId, long seq, Progress progress) {
        return new ChatResponse(Type.PROGRESS, null, conversationId, runId, seq, Instant.now(), null, progress, null,
                null, null);
    }

    public static ChatResponse reply(UUID runId, Long conversationId, long seq, ReplyEnvelope reply) {
        return new ChatResponse(Type.REPLY, null, conversationId, runId, seq, Instant.now(), null, null, reply, null,
                null);
    }

    public static ChatResponse interaction(UUID runId, Long conversationId, long seq, PendingInteraction interaction) {
        return new ChatResponse(Type.INTERACTION, null, conversationId, runId, seq, Instant.now(), null, null, null,
                interaction, null);
    }

    public static ChatResponse error(UUID runId, Long conversationId, Long seq, Error error) {
        return new ChatResponse(Type.ERROR, null, conversationId, runId, seq, Instant.now(), null, null, null, null,
                error);
    }

    /** The refusal of a client request; only the requesting client gets it. */
    public static ChatResponse refused(String requestId, Long conversationId, UUID runId, ErrorCode code,
                                       String message, boolean retryable) {
        return new ChatResponse(Type.ERROR, requestId, conversationId, runId, null, Instant.now(), null, null, null,
                null, new Error(code, message, retryable));
    }
}
