package com.rmrx.agenticshop.ai2.events;

import com.rmrx.agenticshop.ai2.contract.tool.ToolResult;
import com.rmrx.agenticshop.ai2.graph.agent.pending.PendingInteraction;
import com.rmrx.agenticshop.ai2.graph.agent.response.AgentResponse;

import java.util.UUID;

/**
 * The event sink of one run (obtained from {@link RunEvents}); knows nothing about transports. seq and {@code at} are
 * assigned here, safely across threads. {@link #complete}, {@link #pause} and {@link #fail} emit the last event and
 * close the stream; later emits are ignored.
 */
public interface AgentEventPublisher {

    UUID runId();

    void runStarted(Long conversationId);

    void toolStarted(String toolName, String progressLabel);

    /** errorCode is null on success. */
    void toolFinished(String toolName, boolean success, String errorCode, long durationMs);

    /** Emits FINAL and closes the stream. */
    void complete(AgentResponse response, ToolResult displayed);

    /** Emits PAUSED and closes the stream (a resume opens a new one, see {@link RunEvents#open}). */
    void pause(PendingInteraction interaction);

    /** Emits ERROR and closes the stream. */
    void fail(String code, String message, boolean retryable);

    boolean isClosed();
}
