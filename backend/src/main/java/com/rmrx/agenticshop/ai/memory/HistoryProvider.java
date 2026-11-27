package com.rmrx.agenticshop.ai.memory;

import com.rmrx.agenticshop.ai.conversation.message.ConversationMessageStore;
import com.rmrx.agenticshop.ai.graph.state.RunContext;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The conversation so far, as the model sees it before the current run's own messages: the summary of older
 * messages, then the messages after it (earlier runs only, see {@link HistoryProjection}). Summarizes first when due.
 */
@Component
public class HistoryProvider {

    private final ConversationSummarizer summarizer;
    private final ConversationMemoryStore memories;
    private final ConversationMessageStore messages;
    private final AiMemoryProperties props;

    public HistoryProvider(ConversationSummarizer summarizer, ConversationMemoryStore memories,
                           ConversationMessageStore messages, AiMemoryProperties props) {
        this.summarizer = summarizer;
        this.memories = memories;
        this.messages = messages;
        this.props = props;
    }

    public List<ChatMessage> history(RunContext context) {
        summarizer.summarizeIfDue(context);
        return load(context.conversationId(), context.runId());
    }

    /**
     * The history the next run would start from, as it stands now: the summary, then every stored message after it.
     * Read-only: never summarizes, so it is what the model gets before any summary that run may make.
     */
    public List<ChatMessage> currentHistory(Long conversationId) {
        return load(conversationId, null);
    }

    private List<ChatMessage> load(Long conversationId, UUID excludedRunId) {
        Optional<ConversationMemory> memory = memories.find(conversationId);
        long through = memory.map(ConversationMemory::summarizedThroughMessageId).orElse(0L);

        List<ChatMessage> history = new ArrayList<>();
        memory.ifPresent(m -> history.add(SystemMessage.from(
                "Summary of the earlier part of this conversation:\n" + m.summary())));
        history.addAll(HistoryProjection.toChatMessages(messages.listAfter(conversationId, through, excludedRunId)));
        return history;
    }

    /** The conversation's memory now (summary size, messages after it) and the limits that apply to it. */
    public MemoryState memoryState(Long conversationId) {
        Optional<ConversationMemory> memory = memories.find(conversationId);
        long through = memory.map(ConversationMemory::summarizedThroughMessageId).orElse(0L);
        return new MemoryState(memory.isPresent(), memory.map(m -> m.summary().length()).orElse(0),
                props.maxSummaryChars(), props.hardLimitChars(), messages.countAfter(conversationId, through, null),
                props.summarizeAfter(), props.keepRecent());
    }
}
