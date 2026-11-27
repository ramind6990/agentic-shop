package com.rmrx.agenticshop.ai2.model;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * The ai2 agent's model ({@code app.ai2.model}), independent of the old {@code app.ai} settings.
 *
 * @param apiKey          OpenAI key; blank → the app starts, but every ai2 model call fails
 * @param temperature     null → the model's default (reasoning models reject any other value)
 * @param maxOutputTokens reasoning tokens count too, so keep it well above the reply's size
 * @param reasoningEffort {@code none | minimal | low | medium | high}; null → the model's default
 * @param timeout         read timeout of one model call
 * @param retry           retries of the agent's model calls on transient errors (rate limits, timeouts, 5xx)
 */
@ConfigurationProperties("app.ai2.model")
public record Ai2ModelProperties(
        String apiKey,
        @DefaultValue("https://api.openai.com/v1") String baseUrl,
        @DefaultValue("gpt-6-luna") String modelName,
        Double temperature,
        @DefaultValue("4000") int maxOutputTokens,
        String reasoningEffort,
        @DefaultValue("60s") Duration timeout,
        boolean logRequests,
        boolean logResponses,
        @DefaultValue Retry retry) {

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * @param maxRetries     retries after the first attempt (0 = none)
     * @param initialBackoff wait before the first retry; doubled for each next one (with ±20% jitter)
     * @param maxBackoff     the longest wait between two attempts
     */
    public record Retry(
            @DefaultValue("5") int maxRetries,
            @DefaultValue("1s") Duration initialBackoff,
            @DefaultValue("20s") Duration maxBackoff) {
    }
}
