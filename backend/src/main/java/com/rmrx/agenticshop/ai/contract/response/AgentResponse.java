package com.rmrx.agenticshop.ai.contract.response;

import com.rmrx.agenticshop.ai.contract.block.Block;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The versioned envelope of every agent reply: a short markdown text plus structured blocks, citations, quick
 * replies and reply-level actions (buttons not tied to one block, e.g. TALK_TO_HUMAN) for the chat UI. Lists are
 * never null. messageId is null until the reply is persisted.
 *
 * <p>{@code actions} was added within schemaVersion 1: it is optional on read (envelopes stored before it existed
 * deserialize with an empty list), so no version bump is needed.
 */
public record AgentResponse(int schemaVersion, ResponseStatus status, String text, List<Block> blocks,
                            List<Citation> citations, List<SuggestedReply> suggestedReplies, List<ItemAction> actions,
                            UUID runId, Long messageId, Instant createdAt) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public AgentResponse {
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
        citations = citations == null ? List.of() : List.copyOf(citations);
        suggestedReplies = suggestedReplies == null ? List.of() : List.copyOf(suggestedReplies);
        actions = actions == null ? List.of() : List.copyOf(actions);
    }

    /** A reply without reply-level actions. */
    public AgentResponse(int schemaVersion, ResponseStatus status, String text, List<Block> blocks,
                         List<Citation> citations, List<SuggestedReply> suggestedReplies, UUID runId,
                         Long messageId, Instant createdAt) {
        this(schemaVersion, status, text, blocks, citations, suggestedReplies, List.of(), runId, messageId,
                createdAt);
    }

    /** The same reply with its messageId set once it is persisted. */
    public AgentResponse withMessageId(Long id) {
        return new AgentResponse(schemaVersion, status, text, blocks, citations, suggestedReplies, actions, runId,
                id, createdAt);
    }
}
