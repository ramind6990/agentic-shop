package com.rmrx.agenticshop.ai.contract.response;

import com.rmrx.agenticshop.ai.contract.block.Block;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The versioned envelope of every agent reply: a short markdown text plus structured blocks, citations and quick
 * replies for the chat UI. Lists are never null. messageId is null until the reply is persisted.
 */
public record AgentResponse(int schemaVersion, ResponseStatus status, String text, List<Block> blocks,
                            List<Citation> citations, List<SuggestedReply> suggestedReplies, UUID runId,
                            Long messageId, Instant createdAt) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public AgentResponse {
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
        citations = citations == null ? List.of() : List.copyOf(citations);
        suggestedReplies = suggestedReplies == null ? List.of() : List.copyOf(suggestedReplies);
    }
}
