package com.rmrx.agenticshop.ai2.model;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Which system prompt the ai2 agent uses ({@code app.ai2.prompt}).
 *
 * @param version the prompt version, e.g. {@code v2} (see {@link SystemPrompt})
 */
@ConfigurationProperties("app.ai2.prompt")
public record Ai2PromptProperties(@DefaultValue("v2") String version) {
}
