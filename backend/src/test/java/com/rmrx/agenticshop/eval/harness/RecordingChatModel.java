package com.rmrx.agenticshop.eval.harness;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Wraps the agent's chat model ({@code aiChatModel}) and records every call: which tools the model called with which
 * arguments (unredacted, with call ids, so an approval can be matched to its tool call), what results it was given,
 * tokens and latency. Scenarios run one at a time, so calls between two {@link #mark()}s belong to one run segment.
 */
public final class RecordingChatModel implements ChatModel {

    private final ChatModel delegate;
    private final List<ModelCallRecord> calls = new ArrayList<>();

    public RecordingChatModel(ChatModel delegate) {
        this.delegate = delegate;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        long start = System.nanoTime();
        try {
            ChatResponse response = delegate.chat(request);
            add(ModelCallRecord.of(request, response, elapsedMs(start)));
            return response;
        } catch (RuntimeException e) {
            add(ModelCallRecord.failed(request, e, elapsedMs(start)));
            throw e;
        }
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        return chat(request);
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return delegate.defaultRequestParameters();
    }

    /** The delegate's own chat() notifies its listeners; the wrapper has none, so nothing is notified twice. */
    @Override
    public List<ChatModelListener> listeners() {
        return List.of();
    }

    @Override
    public ModelProvider provider() {
        return delegate.provider();
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return delegate.supportedCapabilities();
    }

    /** The number of calls so far; pass it to {@link #since}. */
    public synchronized int mark() {
        return calls.size();
    }

    public synchronized List<ModelCallRecord> since(int mark) {
        return List.copyOf(calls.subList(mark, calls.size()));
    }

    private synchronized void add(ModelCallRecord record) {
        calls.add(record);
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
