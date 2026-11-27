package com.rmrx.agenticshop.ai.conversation;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.rmrx.agenticshop.ai.activerun.ActiveRun;
import com.rmrx.agenticshop.ai.conversation.message.ConversationMessage;
import com.rmrx.agenticshop.ai.conversation.store.ConversationSummary;
import com.rmrx.agenticshop.ai.graph.agent.pending.PendingInteraction;

import java.util.List;
import java.util.UUID;

/**
 * A conversation opened in the chat: its summary fields, its active run (null when idle) and its timeline.
 */
public record ConversationDetail(@JsonUnwrapped ConversationSummary conversation, ActiveRunView activeRun,
                                 List<ConversationMessage> messages) {

    /** RUNNING (a reply is on its way) or PAUSED (waiting for the answer to pendingInteraction). */
    public record ActiveRunView(UUID runId, ActiveRun.Status status, PendingInteraction pendingInteraction) {
    }
}
