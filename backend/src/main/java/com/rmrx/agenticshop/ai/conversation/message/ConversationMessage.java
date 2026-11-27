package com.rmrx.agenticshop.ai.conversation.message;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of the chat timeline ({@code ai_conversation_message}). JSON columns are trees, so the timeline returns
 * them as objects.
 *
 * @param text    USER: the customer's text; ASSISTANT: the reply text; INTERACTION: the question or approval title
 * @param payload ASSISTANT: the AgentResponse; INTERACTION: the pending interaction
 * @param display ASSISTANT only: the shown result {@code {toolName, selectedItemIdentifiers, data, summary}}
 * @param reply   ASSISTANT only: the {@code ReplyEnvelope} the UI renders (null for replies stored before it existed)
 * @param answer  INTERACTION only: the customer's answer
 */
public record ConversationMessage(Long id, Long conversationId, UUID runId, MessageKind kind, String text,
                                  JsonNode payload, JsonNode display, JsonNode reply, UUID interactionId,
                                  InteractionStatus interactionStatus, JsonNode answer, Instant createdAt) {
}
