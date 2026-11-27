package com.rmrx.agenticshop.ai.usage;

import com.rmrx.agenticshop.ai.model.AiModelProperties;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Estimates the size of a conversation's history in tokens, with the tokenizer of the ai model (an estimate: the
 * provider counts the real input when it is sent).
 */
@Component
public class HistoryTokenCounter {

    /** Used when the tokenizer library does not know the configured model; it shares the o200k encoding. */
    private static final String FALLBACK_MODEL = "gpt-4o";

    private static final Logger log = LoggerFactory.getLogger(HistoryTokenCounter.class);

    private final TokenCountEstimator estimator;

    public HistoryTokenCounter(AiModelProperties props) {
        this.estimator = estimatorFor(props.modelName());
    }

    /** The history's tokens, and how many of them the leading summary takes. */
    public HistoryTokens count(List<ChatMessage> history) {
        if (history.isEmpty()) return new HistoryTokens(0, 0);
        int summary = history.getFirst() instanceof SystemMessage s ? estimator.estimateTokenCountInMessage(s) : 0;
        return new HistoryTokens(summary, estimator.estimateTokenCountInMessages(history));
    }

    private static TokenCountEstimator estimatorFor(String modelName) {
        try {
            return new OpenAiTokenCountEstimator(modelName);
        } catch (IllegalArgumentException e) {
            log.info("No tokenizer for model '{}'; estimating history tokens as {}", modelName, FALLBACK_MODEL);
            return new OpenAiTokenCountEstimator(FALLBACK_MODEL);
        }
    }

    /**
     * @param summaryTokens the summary of older messages (0 without one)
     * @param totalTokens   the summary and the messages after it
     */
    public record HistoryTokens(int summaryTokens, int totalTokens) {
    }
}
