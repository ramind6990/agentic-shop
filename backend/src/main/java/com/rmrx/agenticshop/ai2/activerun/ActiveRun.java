package com.rmrx.agenticshop.ai2.activerun;

import com.rmrx.agenticshop.ai2.graph.agent.pending.PendingInteraction;

import java.util.UUID;

/**
 * The one run a conversation may have at a time (a row of {@code ai2_active_run}).
 *
 * @param pendingInteraction what the run waits for; set only while {@link Status#PAUSED}
 */
public record ActiveRun(Long conversationId, UUID runId, Long userId, Status status,
                        PendingInteraction pendingInteraction) {

    public enum Status {
        /** The graph is executing; any input for the conversation is rejected. */
        RUNNING,
        /** The graph waits for the user's answer to {@link #pendingInteraction()}. */
        PAUSED
    }
}
