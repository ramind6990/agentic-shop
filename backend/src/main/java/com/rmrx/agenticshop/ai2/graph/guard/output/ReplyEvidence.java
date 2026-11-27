package com.rmrx.agenticshop.ai2.graph.guard.output;

import com.rmrx.agenticshop.ai2.graph.state.RunState;
import com.rmrx.agenticshop.ai2.memory.HistoryProvider;
import com.rmrx.agenticshop.ai2.model.SystemPrompt;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * What a final reply may rely on: who the user is (role, refund limits), the conversation so far (summary, earlier
 * messages) and this run's own messages (the user's message, tool results, answers). Never the model's own text of
 * this run.
 */
@Component
public class ReplyEvidence {

    private final HistoryProvider history;

    public ReplyEvidence(HistoryProvider history) {
        this.history = history;
    }

    /** Who the user is, then the messages oldest first, one line per message. */
    public List<String> collect(RunState state) {
        List<String> lines = new ArrayList<>();
        lines.add("(context) The user: " + SystemPrompt.userFacts(state.context()));
        Long conversationId = state.context().conversationId();
        if (conversationId != null) {
            history.currentHistory(conversationId).forEach(m -> add(lines, "earlier", m));
        }
        state.messages().forEach(m -> {
            if (!(m instanceof AiMessage)) add(lines, "now", m);
        });
        return lines;
    }

    private static void add(List<String> lines, String when, ChatMessage message) {
        String text = switch (message) {
            case UserMessage u when u.hasSingleText() -> "user: " + u.singleText();
            case AiMessage a when a.text() != null -> "assistant: " + a.text();
            case SystemMessage s -> s.text();
            case ToolExecutionResultMessage r -> "tool " + r.toolName() + ": " + r.text();
            default -> null;
        };
        if (text != null) lines.add("(" + when + ") " + text);
    }
}
