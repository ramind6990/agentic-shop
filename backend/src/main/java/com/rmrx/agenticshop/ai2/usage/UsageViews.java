package com.rmrx.agenticshop.ai2.usage;

import com.rmrx.agenticshop.ai2.memory.MemoryState;

import java.time.Instant;
import java.util.Map;

/** Responses of the usage endpoints. All numbers are tokens. */
public final class UsageViews {

    private UsageViews() {
    }

    /** A user's usage in [from, to), in total and per issuer (both issuers always present). */
    public record UserUsage(Long userId, Instant from, Instant to, TokenTotals total,
                            Map<UsageIssuer, TokenTotals> byIssuer) {
    }

    /** A conversation's usage over its lifetime, and how full its context is. */
    public record ConversationUsage(Long conversationId, TokenTotals total, Map<UsageIssuer, TokenTotals> byIssuer,
                                    ContextUsage context) {
    }

    /**
     * How much the model has in mind for this conversation.
     *
     * @param lastCallInputTokens input tokens of the conversation's latest agent call (system prompt, tools, summary,
     *                            earlier messages and that run's own messages); null before the first call
     * @param lastCallAt          when that call was made
     * @param memory              the conversation's memory as the history provider sees it (summary and the messages
     *                            not folded into it yet)
     */
    public record ContextUsage(Integer lastCallInputTokens, Instant lastCallAt, MemoryState memory) {
    }
}
