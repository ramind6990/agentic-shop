package com.rmrx.agenticshop.ai2.model;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Budget of one ai2 run ({@code app.ai2.run}); the model-call limits are still in {@code RunLimits}.
 *
 * @param maxTokens input + output tokens of the run's own model calls; once reached, the next model call is made with
 *                  tools off so the model answers with what it already has (a soft limit: that last answer, and its
 *                  validation retries, still run)
 */
@ConfigurationProperties("app.ai2.run")
public record Ai2RunProperties(@DefaultValue("200000") int maxTokens) {
}
