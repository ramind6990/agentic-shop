package com.rmrx.agenticshop.ai2.memory;

import com.rmrx.agenticshop.ai2.conversation.message.ConversationMessage;
import com.rmrx.agenticshop.ai2.conversation.message.ConversationMessageStore;
import com.rmrx.agenticshop.ai2.conversation.store.ConversationStore;
import com.rmrx.agenticshop.ai2.model.Ai2ModelConfig;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Folds a conversation's older messages into its summary. Runs inside a run (the conversation's active run is held,
 * so nothing else writes its memory meanwhile), before the history is built, and only when due: more than
 * {@code summarizeAfter} messages of earlier runs are not in the summary. The newest {@code keepRecent} stay word for
 * word. A summary that ends up over {@code hardLimitChars} closes the conversation.
 */
@Component("ai2ConversationSummarizer")
public class ConversationSummarizer {

    private static final Logger log = LoggerFactory.getLogger(ConversationSummarizer.class);

    private final ChatModel model;
    private final ConversationMessageStore messages;
    private final ConversationMemoryStore memories;
    private final ConversationStore conversations;
    private final Ai2MemoryProperties props;
    private final String instructions;

    public ConversationSummarizer(@Qualifier(Ai2ModelConfig.CHAT_MODEL) ChatModel model,
                                  ConversationMessageStore messages, ConversationMemoryStore memories,
                                  ConversationStore conversations, Ai2MemoryProperties props) {
        this.model = model;
        this.messages = messages;
        this.memories = memories;
        this.conversations = conversations;
        this.props = props;
        this.instructions = load("prompts/ai2/summary-" + props.promptVersion() + ".md")
                .replace("{{maxSummaryChars}}", String.valueOf(props.maxSummaryChars())).strip();
    }

    /**
     * Summarizes when due; never throws (a failure only leaves the history unsummarized).
     *
     * @param currentRunId the run asking; its own messages are never summarized
     */
    public void summarizeIfDue(Long conversationId, UUID currentRunId) {
        try {
            Optional<ConversationMemory> memory = memories.find(conversationId);
            long through = memory.map(ConversationMemory::summarizedThroughMessageId).orElse(0L);
            if (messages.countAfter(conversationId, through, currentRunId) <= props.summarizeAfter()) return;

            List<ConversationMessage> unsummarized = messages.listAfter(conversationId, through, currentRunId);
            List<ConversationMessage> fold = unsummarized.subList(0, unsummarized.size() - props.keepRecent());
            String summary = summarize(memory.map(ConversationMemory::summary).orElse(""),
                    HistoryProjection.toTranscript(fold));
            memories.save(conversationId, summary, fold.getLast().id());
            log.info("ai2: conversation {} summarized through message {} ({} chars)", conversationId,
                    fold.getLast().id(), summary.length());

            if (summary.length() > props.hardLimitChars()) {
                conversations.close(conversationId);
                log.warn("ai2: conversation {} closed, its summary has {} chars (limit {})", conversationId,
                        summary.length(), props.hardLimitChars());
            }
        } catch (RuntimeException e) {
            log.warn("ai2: summarizing conversation {} failed; the history stays unsummarized", conversationId, e);
        }
    }

    private String summarize(String currentSummary, String transcript) {
        String input = "Current summary:\n" + (currentSummary.isBlank() ? "(none)" : currentSummary)
                + "\n\nMessages after it:\n" + transcript;
        String summary = model.chat(ChatRequest.builder()
                .messages(SystemMessage.from(instructions), UserMessage.from(input))
                .build()).aiMessage().text();
        if (summary == null || summary.isBlank()) throw new IllegalStateException("empty summary");
        return summary.strip();
    }

    private static String load(String location) {
        try {
            return new ClassPathResource(location).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read the ai2 summary prompt " + location, e);
        }
    }
}
