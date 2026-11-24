package com.rmrx.agenticshop.ai.events;

import com.rmrx.agenticshop.ai.contract.block.Block;
import com.rmrx.agenticshop.ai.contract.response.AgentResponse;

import java.util.UUID;

/**
 * The event sink of one run; the only event API agent code uses (obtained from {@link RunEvents#open}). It knows
 * nothing about transports. seq (1, 2, 3, … per run) and {@code at} are assigned here, safely across threads.
 * {@link #complete} and {@link #fail} emit the last event and close the run; later emits are ignored.
 */
public interface AgentEventPublisher {

    UUID runId();

    void runStarted(Long ticketId);

    void tokenDelta(String text);

    void toolStarted(String toolName, String progressLabel);

    /** errorCode is null on success. */
    void toolFinished(String toolName, boolean success, String errorCode, long durationMs);

    void blockReady(Block block);

    /** Emits FINAL and closes the run. */
    void complete(AgentResponse response);

    /** Emits ERROR and closes the run. */
    void fail(String code, String message, boolean retryable);

    boolean isClosed();
}
