package com.rmrx.agenticshop.ai.config;

import com.rmrx.agenticshop.ai.usage.RecordingEmbeddingModel;
import com.rmrx.agenticshop.ai.usage.UsageChatModelListener;
import com.rmrx.agenticshop.ai.usage.UsageRecorder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Wires the AI layer by hand (LangChain4j as a plain library, no starters). Model beans exist only with
 * {@code app.ai.enabled=true}; then an API key is required.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    public static final String SUMMARY_MODEL = "aiSummaryChatModel";

    /** Runs agent turns off the request thread; one virtual thread per turn. */
    @Bean(destroyMethod = "close")
    public ExecutorService aiExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Configuration
    @ConditionalOnBooleanProperty("app.ai.enabled")
    static class Models {

        private final AiProperties props;
        /** Bills every chat call to the bound RunScope and traces it. */
        private final List<ChatModelListener> listeners;
        private final UsageRecorder usageRecorder;

        Models(AiProperties props, UsageChatModelListener usageListener, UsageRecorder usageRecorder) {
            this.props = props;
            this.listeners = List.of(usageListener);
            this.usageRecorder = usageRecorder;
            if (!props.openai().hasApiKey()) {
                throw new IllegalStateException("app.ai.enabled=true but OPENAI_API_KEY is not set");
            }
            log.info("AI enabled: chat={}, summary={}, embedding={}",
                    props.chat().model(), props.chat().summaryModel(), props.embedding().model());
        }

        /** The agent model; inject the summary model with {@code @Qualifier(AiConfig.SUMMARY_MODEL)}. */
        @Bean
        @Primary
        ChatModel aiChatModel() {
            return chatModel(props.chat().model());
        }

        @Bean(SUMMARY_MODEL)
        ChatModel aiSummaryChatModel() {
            return chatModel(props.chat().summaryModel());
        }

        @Bean
        StreamingChatModel aiStreamingChatModel() {
            AiProperties.OpenAi openai = props.openai();
            AiProperties.Chat chat = props.chat();
            return OpenAiStreamingChatModel.builder()
                    .baseUrl(openai.baseUrl())
                    .apiKey(openai.apiKey())
                    .modelName(chat.model())
                    .temperature(chat.temperature())
                    .maxCompletionTokens(chat.maxOutputTokens())
                    .timeout(chat.timeout())
                    .logRequests(openai.logRequests())
                    .logResponses(openai.logResponses())
                    .listeners(listeners)
                    .build();
        }

        /** The OpenAI embedding model wrapped so every call is written to the usage ledger. */
        @Bean
        EmbeddingModel aiEmbeddingModel() {
            AiProperties.OpenAi openai = props.openai();
            AiProperties.Embedding embedding = props.embedding();
            EmbeddingModel model = OpenAiEmbeddingModel.builder()
                    .baseUrl(openai.baseUrl())
                    .apiKey(openai.apiKey())
                    .modelName(embedding.model())
                    .dimensions(embedding.dimensions())
                    .timeout(embedding.timeout())
                    .logRequests(openai.logRequests())
                    .logResponses(openai.logResponses())
                    .build();
            return new RecordingEmbeddingModel(model, usageRecorder, UsageRecorder.OPENAI, embedding.model());
        }

        private ChatModel chatModel(String model) {
            AiProperties.OpenAi openai = props.openai();
            AiProperties.Chat chat = props.chat();
            return OpenAiChatModel.builder()
                    .baseUrl(openai.baseUrl())
                    .apiKey(openai.apiKey())
                    .modelName(model)
                    .temperature(chat.temperature())
                    .maxCompletionTokens(chat.maxOutputTokens())
                    .timeout(chat.timeout())
                    .logRequests(openai.logRequests())
                    .logResponses(openai.logResponses())
                    .listeners(listeners)
                    .build();
        }
    }
}
