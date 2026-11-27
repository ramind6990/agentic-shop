package com.rmrx.agenticshop.ai.usage;

import java.util.Collection;

/**
 * Summed token usage of some model calls. {@code totalTokens} = input + output (cached input and reasoning tokens are
 * already part of those).
 */
public record TokenTotals(long calls, long inputTokens, long cachedInputTokens, long outputTokens,
                          long reasoningTokens, long totalTokens) {

    public static final TokenTotals ZERO = of(0, 0, 0, 0, 0);

    public static TokenTotals of(long calls, long input, long cached, long output, long reasoning) {
        return new TokenTotals(calls, input, cached, output, reasoning, input + output);
    }

    public static TokenTotals sum(Collection<TokenTotals> totals) {
        return totals.stream().reduce(ZERO, TokenTotals::plus);
    }

    public TokenTotals plus(TokenTotals other) {
        return of(calls + other.calls, inputTokens + other.inputTokens, cachedInputTokens + other.cachedInputTokens,
                outputTokens + other.outputTokens, reasoningTokens + other.reasoningTokens);
    }
}
