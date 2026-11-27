package com.rmrx.agenticshop.ai.model;

import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiResponsesChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * The agent's chat model. Injected by name only ({@code @Qualifier(AiModelConfig.CHAT_MODEL)}): it is not a default
 * candidate, so code asking for "a ChatModel" by type never gets it by accident. Agent code calls it only through
 * {@link ModelGateway} (retries, trace, token usage). The Responses model has no retries of its own.
 */
@Configuration
@EnableConfigurationProperties({AiModelProperties.class, AiPromptProperties.class, AiReplyProperties.class,
        AiRunProperties.class})
public class AiModelConfig {

    public static final String CHAT_MODEL = "aiChatModel";

    private static final Logger log = LoggerFactory.getLogger(AiModelConfig.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    @Bean(name = CHAT_MODEL, defaultCandidate = false)
    public ChatModel aiChatModel(AiModelProperties props) {
        if (!props.hasApiKey()) {
            log.warn("ai model: no API key (app.ai.model.api-key / OPENAI_API_KEY); ai runs will fail");
            return new UnconfiguredChatModel();
        }
        log.info("ai model: {} (OpenAI Responses API)", props.modelName());
        // The Responses API (/v1/responses), not Chat Completions: reasoning models such as gpt-6-luna reject
        // function tools together with reasoning on /v1/chat/completions.
        return OpenAiResponsesChatModel.builder()
                .baseUrl(props.baseUrl())
                .apiKey(props.apiKey())
                .modelName(props.modelName())
                .temperature(props.temperature())
                .maxOutputTokens(props.maxOutputTokens())
                .reasoningEffort(props.reasoningEffort())
                // nothing is kept on OpenAI's side: every request carries the run's full messages
                .store(false)
                .httpClientBuilder(HttpClientBuilderLoader.loadHttpClientBuilder()
                        .connectTimeout(CONNECT_TIMEOUT)
                        .readTimeout(props.timeout()))
                .logRequests(props.logRequests())
                .logResponses(props.logResponses())
                .build();
    }

    /** Stands in when no API key is configured, so the app still starts; every call fails with a clear message. */
    static final class UnconfiguredChatModel implements ChatModel {

        @Override
        public ChatResponse doChat(ChatRequest request) {
            throw new IllegalStateException("ai model: OPENAI_API_KEY is not set (app.ai.model.api-key)");
        }
    }
}
