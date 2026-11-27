package com.rmrx.agenticshop.ai.memory;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Conversation memory of the ai agent ({@code app.ai.memory}).
 *
 * @param summarizeAfter  summarize once more than this many messages of earlier runs are not in the summary
 * @param keepRecent      the newest messages always kept word for word (not folded into the summary)
 * @param maxSummaryChars the size the summarizer is asked to keep the summary under
 * @param hardLimitChars  a stored summary longer than this closes the conversation (a new one must be started)
 * @param promptVersion   the summarizer prompt {@code prompts/ai/summary-<version>.md}
 */
@ConfigurationProperties("app.ai.memory")
public record AiMemoryProperties(
        @DefaultValue("20") int summarizeAfter,
        @DefaultValue("6") int keepRecent,
        @DefaultValue("3000") int maxSummaryChars,
        @DefaultValue("6000") int hardLimitChars,
        @DefaultValue("v1") String promptVersion) {
}
