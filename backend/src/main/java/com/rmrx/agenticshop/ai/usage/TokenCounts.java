package com.rmrx.agenticshop.ai.usage;

import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.output.TokenUsage;

/**
 * The tokens of one model call. {@code cached} is the part of {@code input} served from the provider's prompt cache,
 * {@code reasoning} the part of {@code output} spent on reasoning (both 0 when the provider does not report them).
 */
public record TokenCounts(int input, int cached, int output, int reasoning) {

    public static final TokenCounts ZERO = new TokenCounts(0, 0, 0, 0);

    public static TokenCounts of(TokenUsage usage) {
        if (usage == null) return ZERO;
        int cached = 0;
        int reasoning = 0;
        if (usage instanceof OpenAiTokenUsage openAi) {
            if (openAi.inputTokensDetails() != null) cached = nz(openAi.inputTokensDetails().cachedTokens());
            if (openAi.outputTokensDetails() != null) reasoning = nz(openAi.outputTokensDetails().reasoningTokens());
        }
        return new TokenCounts(nz(usage.inputTokenCount()), cached, nz(usage.outputTokenCount()), reasoning);
    }

    /** Input plus output tokens (cached and reasoning tokens are already part of them). */
    public int total() {
        return input + output;
    }

    private static int nz(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }
}
