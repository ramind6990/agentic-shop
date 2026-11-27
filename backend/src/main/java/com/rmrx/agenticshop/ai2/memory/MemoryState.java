package com.rmrx.agenticshop.ai2.memory;

/**
 * A conversation's memory as the history provider sees it: the summary of older messages and the messages kept word
 * for word after it. The next run summarizes once {@code messagesSinceSummary} exceeds {@code summarizeAfter}; a
 * summary over {@code hardLimitChars} closes the conversation.
 *
 * @param summarized           whether older messages were folded into a summary yet
 * @param summaryChars         size of that summary (0 without one)
 * @param messagesSinceSummary chat messages after the summary (what the model gets word for word)
 */
public record MemoryState(boolean summarized, int summaryChars, int maxSummaryChars, int hardLimitChars,
                          int messagesSinceSummary, int summarizeAfter, int keepRecent) {
}
