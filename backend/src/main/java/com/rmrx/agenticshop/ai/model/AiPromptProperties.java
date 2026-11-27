package com.rmrx.agenticshop.ai.model;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Which system prompt the ai agent uses ({@code app.ai.prompt}).
 *
 * @param version the prompt version, e.g. {@code v2} (see {@link SystemPrompt})
 */
@ConfigurationProperties("app.ai.prompt")
public record AiPromptProperties(@DefaultValue("v3") String version) {
}
