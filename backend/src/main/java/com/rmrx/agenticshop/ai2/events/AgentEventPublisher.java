package com.rmrx.agenticshop.ai2.events;

import com.rmrx.agenticshop.ai2.contract.reply.ReplyEnvelope;
import com.rmrx.agenticshop.ai2.graph.agent.pending.PendingInteraction;

import java.util.UUID;

/**
 * The event sink of one run (obtained from {@link RunEvents}); knows nothing about transports. Every event is a
 * {@link ChatResponse}; seq and {@code at} are assigned here, safely across threads. {@link #complete}, {@link #pause}
 * and {@link #fail} emit the last event and close the stream; later emits are ignored.
 */
public interface AgentEventPublisher {

    UUID runId();

    /** PROGRESS RUN_STARTED. */
    void runStarted();

    /** PROGRESS TOOL_STARTED with the user-facing label. */
    void toolStarted(String toolName, String progressLabel);

    /** PROGRESS TOOL_FINISHED. */
    void toolFinished(String toolName, boolean success, long durationMs);

    /** REPLY; closes the stream. */
    void complete(ReplyEnvelope reply);

    /** INTERACTION; closes the stream (a resume opens a new one, see {@link RunEvents#open}). */
    void pause(PendingInteraction interaction);

    /** ERROR; closes the stream. */
    void fail(ChatResponse.ErrorCode code, String message, boolean retryable);

    boolean isClosed();
}
