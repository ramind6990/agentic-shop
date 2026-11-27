package com.rmrx.agenticshop.ai.memory;

import com.rmrx.agenticshop.ai.conversation.message.ConversationMessage;
import com.rmrx.agenticshop.ai.conversation.message.ConversationMessageStore;
import com.rmrx.agenticshop.ai.conversation.store.ConversationStore;
import com.rmrx.agenticshop.ai.graph.guard.pii.PiiRedactor;
import com.rmrx.agenticshop.ai.graph.state.RunContext;
import com.rmrx.agenticshop.ai.model.ModelCall;
import com.rmrx.agenticshop.ai.model.ModelGateway;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * Folds a conversation's older messages into its summary. Runs inside a run (the conversation's active run is held,
 * so nothing else writes its memory meanwhile), before the history is built, and only when due: more than
 * {@code summarizeAfter} messages of earlier runs are not in the summary. The newest {@code keepRecent} stay word for
 * word. A summary that ends up over {@code hardLimitChars} closes the conversation. The model call is SYSTEM usage
 * (traced in the run that triggered it) and is not retried.
 */
@Component("aiConversationSummarizer")
public class ConversationSummarizer {

    private static final Logger log = LoggerFactory.getLogger(ConversationSummarizer.class);

    private final ModelGateway model;
    private final ConversationMessageStore messages;
    private final ConversationMemoryStore memories;
    private final ConversationStore conversations;
    private final AiMemoryProperties props;
    private final PiiRedactor pii;
    private final String instructions;

    public ConversationSummarizer(ModelGateway model, ConversationMessageStore messages,
                                  ConversationMemoryStore memories, ConversationStore conversations,
                                  AiMemoryProperties props, PiiRedactor pii) {
        this.model = model;
        this.pii = pii;
        this.messages = messages;
        this.memories = memories;
        this.conversations = conversations;
        this.props = props;
        this.instructions = load("prompts/ai/summary-" + props.promptVersion() + ".md")
                .replace("{{maxSummaryChars}}", String.valueOf(props.maxSummaryChars())).strip();
    }

    /**
     * Summarizes when due; never throws (a failure only leaves the history unsummarized). The asking run's own
     * messages are never summarized.
     */
    public void summarizeIfDue(RunContext context) {
        Long conversationId = context.conversationId();
        try {
            Optional<ConversationMemory> memory = memories.find(conversationId);
            long through = memory.map(ConversationMemory::summarizedThroughMessageId).orElse(0L);
            if (messages.countAfter(conversationId, through, context.runId()) <= props.summarizeAfter()) return;

            List<ConversationMessage> unsummarized = messages.listAfter(conversationId, through, context.runId());
            List<ConversationMessage> fold = unsummarized.subList(0, unsummarized.size() - props.keepRecent());
            String note = "fold " + fold.size() + " messages (ids " + fold.getFirst().id() + ".." + fold.getLast().id()
                    + ") into the summary";
            // PII guardrail: contact and payment data never goes into the summarizer, nor into the stored summary
            String summary = pii.redact(summarize(ModelCall.summary(context, note),
                    memory.map(ConversationMemory::summary).orElse(""),
                    pii.redact(HistoryProjection.toTranscript(fold))));
            memories.save(conversationId, summary, fold.getLast().id());
            log.info("ai: conversation {} summarized through message {} ({} chars)", conversationId,
                    fold.getLast().id(), summary.length());

            if (summary.length() > props.hardLimitChars()) {
                conversations.close(conversationId);
                log.warn("ai: conversation {} closed, its summary has {} chars (limit {})", conversationId,
                        summary.length(), props.hardLimitChars());
            }
        } catch (RuntimeException e) {
            log.warn("ai: summarizing conversation {} failed; the history stays unsummarized", conversationId, e);
        }
    }

    private String summarize(ModelCall call, String currentSummary, String transcript) {
        String input = "Current summary:\n" + (currentSummary.isBlank() ? "(none)" : currentSummary)
                + "\n\nMessages after it:\n" + transcript;
        String summary = model.chat(ChatRequest.builder()
                .messages(SystemMessage.from(instructions), UserMessage.from(input))
                .build(), call).aiMessage().text();
        if (summary == null || summary.isBlank()) throw new IllegalStateException("empty summary");
        return summary.strip();
    }

    private static String load(String location) {
        try {
            return new ClassPathResource(location).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read the ai summary prompt " + location, e);
        }
    }
}
