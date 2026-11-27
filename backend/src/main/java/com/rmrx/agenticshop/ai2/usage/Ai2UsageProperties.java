package com.rmrx.agenticshop.ai2.usage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * What a conversation's usage is measured against ({@code app.ai2.usage}). Shown to the user as percentages; nothing
 * is blocked when a limit is passed.
 *
 * @param chatTokenLimit    tokens one conversation may use over its lifetime (all model calls, the user's and the
 *                          system's)
 * @param contextTokenLimit tokens of history (summary and the messages after it) the model is meant to get
 */
@ConfigurationProperties("app.ai2.usage")
public record Ai2UsageProperties(
        @DefaultValue("1000000") long chatTokenLimit,
        @DefaultValue("200000") long contextTokenLimit) {
}
