package com.rmrx.agenticshop.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * {@code app.ai.*}: switches, OpenAI connection, model choices and agent limits.
 *
 * <p>With {@code enabled=false} (the default) no model beans are created and the rest of the app runs as before.
 */
@ConfigurationProperties("app.ai")
public record AiProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue OpenAi openai,
        @DefaultValue Chat chat,
        @DefaultValue Embedding embedding,
        @DefaultValue Agent agent,
        @DefaultValue Mock mock,
        @DefaultValue FailureInjection failureInjection) {

    /** OpenAI connection. The key comes from {@code OPENAI_API_KEY}; never commit it. */
    public record OpenAi(
            String apiKey,
            @DefaultValue("https://api.openai.com/v1") String baseUrl,
            @DefaultValue("false") boolean logRequests,
            @DefaultValue("false") boolean logResponses) {

        public boolean hasApiKey() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    /**
     * Chat models: the main agent model and a cheaper one for summaries. Temperature stays unset unless configured,
     * because some models only accept their default.
     */
    public record Chat(
            @DefaultValue("gpt-5-mini") String model,
            @DefaultValue("gpt-5-nano") String summaryModel,
            Double temperature,
            @DefaultValue("4000") int maxOutputTokens,
            @DefaultValue("60s") Duration timeout) {
    }

    /** Embedding model for policy retrieval. {@code dimensions} must match the vector column. */
    public record Embedding(
            @DefaultValue("text-embedding-3-small") String model,
            @DefaultValue("1536") int dimensions,
            @DefaultValue("30s") Duration timeout) {
    }

    /**
     * Agent limits: max model/tool iterations per turn, how many recent turns go into the prompt, and the overall
     * time budget of one turn.
     */
    public record Agent(
            @DefaultValue("8") int stepBudget,
            @DefaultValue("12") int historyWindow,
            @DefaultValue("120s") Duration turnTimeout) {
    }

    /**
     * Mocked tool backends: the scenario used when none is selected, and whether a request may pick one with the
     * {@code X-Mock-Scenario} header (dev and eval only).
     */
    public record Mock(
            @DefaultValue("happy") String defaultScenario,
            @DefaultValue("false") boolean scenarioHeaderEnabled) {
    }

    /**
     * Failure injection in the mocked backends, all off by default. Rates are 0..1 per tool call; latency is added to
     * every call.
     */
    public record FailureInjection(
            @DefaultValue("0") double errorRate,
            @DefaultValue("0") double timeoutRate,
            @DefaultValue("0") double malformedRate,
            @DefaultValue("0ms") Duration extraLatency) {

        public boolean active() {
            return errorRate > 0 || timeoutRate > 0 || malformedRate > 0 || !extraLatency.isZero();
        }
    }
}
