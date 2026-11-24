package com.rmrx.agenticshop.ai.usage;

import com.rmrx.agenticshop.ai.usage.entity.AiUsagePurpose;
import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.embedding.request.EmbeddingParameter;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.request.EmbeddingRequestParameters;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import dev.langchain4j.model.output.TokenUsage;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Decorates an {@link EmbeddingModel} so every call is written to the usage ledger. All the convenience methods
 * ({@code embed(String)}, {@code embed(TextSegment)}, {@code embedAll}) route through {@link #embed(EmbeddingRequest)},
 * which delegates and records.
 *
 * <p>The purpose comes from the bound {@link RunScope}; a chat purpose (CHAT, SUMMARY) is recorded as
 * EMBEDDING_QUERY, since the embedding is the retrieval query of that turn. With no scope it is EMBEDDING_QUERY
 * with no user.
 */
@Slf4j
public class RecordingEmbeddingModel implements EmbeddingModel {

    private final EmbeddingModel delegate;
    private final UsageRecorder usage;
    private final String provider;
    private final String model;

    public RecordingEmbeddingModel(EmbeddingModel delegate, UsageRecorder usage, String provider, String model) {
        this.delegate = delegate;
        this.usage = usage;
        this.provider = provider;
        this.model = model;
    }

    @Override
    public EmbeddingResponse embed(EmbeddingRequest request) {
        EmbeddingResponse response = delegate.embed(request);
        record(response == null ? null : response.tokenUsage(), scopeNow());
        return response;
    }

    @Override
    public CompletableFuture<EmbeddingResponse> embedAsync(EmbeddingRequest request) {
        RunScope scope = scopeNow();
        return delegate.embedAsync(request).whenComplete((response, error) -> {
            if (response != null) record(response.tokenUsage(), scope);
        });
    }

    @Override
    public EmbeddingResponse doEmbed(EmbeddingRequest request) {
        return delegate.doEmbed(request);
    }

    @Override
    public CompletableFuture<EmbeddingResponse> doEmbedAsync(EmbeddingRequest request) {
        return delegate.doEmbedAsync(request);
    }

    @Override
    public List<EmbeddingModelListener> listeners() {
        return delegate.listeners();
    }

    @Override
    public ModelProvider provider() {
        return delegate.provider();
    }

    @Override
    public EmbeddingRequestParameters defaultRequestParameters() {
        return delegate.defaultRequestParameters();
    }

    @Override
    public Set<EmbeddingParameter<?>> supportedParameters() {
        return delegate.supportedParameters();
    }

    @Override
    public Set<ContentType> supportedContentTypes() {
        return delegate.supportedContentTypes();
    }

    @Override
    public int dimension() {
        return delegate.dimension();
    }

    @Override
    public String modelName() {
        return delegate.modelName();
    }

    private static RunScope scopeNow() {
        RunScope scope = RunScopes.current().orElse(null);
        if (scope == null) {
            log.warn("Embedding call without a RunScope: recorded as EMBEDDING_QUERY with no user");
            return RunScope.system(AiUsagePurpose.EMBEDDING_QUERY);
        }
        return switch (scope.purpose()) {
            case CHAT, SUMMARY -> scope.withPurpose(AiUsagePurpose.EMBEDDING_QUERY);
            default -> scope;
        };
    }

    private void record(TokenUsage tokens, RunScope scope) {
        try {
            int input = tokens == null || tokens.inputTokenCount() == null ? 0 : tokens.inputTokenCount();
            usage.record(provider, model, input, 0, 0, scope, false);
        } catch (Exception e) {
            log.error("Failed to record embedding usage", e);
        }
    }
}
