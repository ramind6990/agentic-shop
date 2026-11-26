package com.rmrx.agenticshop.ai2.conversation;

import com.rmrx.agenticshop.ai2.graph.agent.pending.PendingInteraction;

import java.util.UUID;

/**
 * The one run a conversation may have at a time.
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

    static ActiveRun started(Long conversationId, Long userId) {
        return new ActiveRun(conversationId, UUID.randomUUID(), userId, Status.RUNNING, null);
    }

    ActiveRun paused(PendingInteraction interaction) {
        return new ActiveRun(conversationId, runId, userId, Status.PAUSED, interaction);
    }

    ActiveRun running() {
        return new ActiveRun(conversationId, runId, userId, Status.RUNNING, null);
    }
}
