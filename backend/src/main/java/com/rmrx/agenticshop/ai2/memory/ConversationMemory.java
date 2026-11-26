package com.rmrx.agenticshop.ai2.memory;

/**
 * The summary of a conversation's older messages ({@code ai2_conversation_memory}).
 *
 * @param summarizedThroughMessageId every message up to this id is folded into {@code summary}
 */
public record ConversationMemory(Long conversationId, String summary, long summarizedThroughMessageId) {
}
