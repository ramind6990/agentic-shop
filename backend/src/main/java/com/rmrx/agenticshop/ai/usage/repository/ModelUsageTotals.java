package com.rmrx.agenticshop.ai.usage.repository;

import java.math.BigDecimal;

/** Summed usage for one provider and model. */
public record ModelUsageTotals(String provider, String model, long calls, long inputTokens,
                               long cachedInputTokens, long outputTokens, BigDecimal costUsd) {
}
