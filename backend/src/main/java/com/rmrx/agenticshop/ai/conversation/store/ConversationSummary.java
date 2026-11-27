package com.rmrx.agenticshop.ai.conversation.store;

import java.time.Instant;

/**
 * A conversation in the user's list.
 *
 * @param title the start of its first message (null for a conversation without one)
 */
public record ConversationSummary(Long id, String title, Conversation.Status status, Instant updatedAt) {
}
