package com.rmrx.agenticshop.ai.usage.repository;

import java.math.BigDecimal;

/** Summed usage over a set of ledger rows; zeros when there are none. */
public record UsageTotals(long calls, long inputTokens, long cachedInputTokens, long outputTokens,
                          BigDecimal costUsd) {
}
