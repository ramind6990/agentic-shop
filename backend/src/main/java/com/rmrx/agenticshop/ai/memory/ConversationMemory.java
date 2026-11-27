package com.rmrx.agenticshop.ai.memory;

/**
 * The summary of a conversation's older messages ({@code ai_conversation_memory}).
 *
 * @param summarizedThroughMessageId every message up to this id is folded into {@code summary}
 */
public record ConversationMemory(Long conversationId, String summary, long summarizedThroughMessageId) {
}
