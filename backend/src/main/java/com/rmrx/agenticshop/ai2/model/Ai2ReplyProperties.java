package com.rmrx.agenticshop.ai2.model;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Limits of the ai2 agent's final reply ({@code app.ai2.reply}). The system prompt tells the model these numbers and
 * the output validation enforces them, so both always use the same values.
 *
 * @param maxTextLength            characters of the reply text
 * @param maxSuggestions           suggested replies
 * @param maxSuggestionLabelLength characters of a suggested reply's button label
 */
@ConfigurationProperties("app.ai2.reply")
public record Ai2ReplyProperties(
        @DefaultValue("600") int maxTextLength,
        @DefaultValue("3") int maxSuggestions,
        @DefaultValue("40") int maxSuggestionLabelLength) {
}
