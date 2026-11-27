package com.rmrx.agenticshop.ai.model;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Budget of one ai run ({@code app.ai.run}); the model-call limits are still in {@code RunLimits}.
 *
 * @param maxTokens input + output tokens of the run's own model calls; once reached, the next model call is made with
 *                  tools off so the model answers with what it already has (a soft limit: that last answer, and its
 *                  validation retries, still run)
 */
@ConfigurationProperties("app.ai.run")
public record AiRunProperties(@DefaultValue("200000") int maxTokens) {
}
