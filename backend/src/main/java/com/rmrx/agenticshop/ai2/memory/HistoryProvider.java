package com.rmrx.agenticshop.ai2.memory;

import com.rmrx.agenticshop.ai2.conversation.message.ConversationMessageStore;
import com.rmrx.agenticshop.ai2.graph.state.RunContext;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The conversation so far, as the model sees it before the current run's own messages: the summary of older
 * messages, then the messages after it (earlier runs only, see {@link HistoryProjection}). Summarizes first when due.
 */
@Component
public class HistoryProvider {

    private final ConversationSummarizer summarizer;
    private final ConversationMemoryStore memories;
    private final ConversationMessageStore messages;

    public HistoryProvider(ConversationSummarizer summarizer, ConversationMemoryStore memories,
                           ConversationMessageStore messages) {
        this.summarizer = summarizer;
        this.memories = memories;
        this.messages = messages;
    }

    public List<ChatMessage> history(RunContext context) {
        summarizer.summarizeIfDue(context.conversationId(), context.runId());
        Optional<ConversationMemory> memory = memories.find(context.conversationId());
        long through = memory.map(ConversationMemory::summarizedThroughMessageId).orElse(0L);

        List<ChatMessage> history = new ArrayList<>();
        memory.ifPresent(m -> history.add(SystemMessage.from(
                "Summary of the earlier part of this conversation:\n" + m.summary())));
        history.addAll(HistoryProjection.toChatMessages(
                messages.listAfter(context.conversationId(), through, context.runId())));
        return history;
    }
}
