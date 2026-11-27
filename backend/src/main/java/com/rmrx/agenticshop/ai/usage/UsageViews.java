package com.rmrx.agenticshop.ai.usage;

import com.rmrx.agenticshop.ai.memory.MemoryState;

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

    /**
     * A conversation's usage over its lifetime, and how full its context is.
     *
     * @param tokenLimit what {@code total} is measured against ({@code app.ai.usage.chat-token-limit})
     */
    public record ConversationUsage(Long conversationId, TokenTotals total, Map<UsageIssuer, TokenTotals> byIssuer,
                                    long tokenLimit, ContextUsage context) {
    }

    /**
     * The conversation's history as the next run would get it, before any summary that run may make. Estimated with
     * the model's tokenizer; the instructions and tools sent with it are not counted.
     *
     * @param historyTokens the summary and the messages after it
     * @param summaryTokens the summary alone (0 without one)
     * @param tokenLimit    what {@code historyTokens} is measured against ({@code app.ai.usage.context-token-limit})
     * @param memory        the conversation's memory as the history provider sees it (summary and the messages not
     *                      folded into it yet)
     */
    public record ContextUsage(int historyTokens, int summaryTokens, long tokenLimit, MemoryState memory) {
    }
}
