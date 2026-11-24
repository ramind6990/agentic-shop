package com.rmrx.agenticshop.ai.graph;

import com.rmrx.agenticshop.ai.config.AiProperties;
import com.rmrx.agenticshop.ai.contract.response.AgentResponse;
import com.rmrx.agenticshop.support.entity.AuthorType;
import com.rmrx.agenticshop.support.entity.MessageKind;
import com.rmrx.agenticshop.support.entity.TicketMessage;
import com.rmrx.agenticshop.support.repository.TicketMessageRepository;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Builds the model input of a turn: the versioned system prompt plus the identity line, the last
 * {@code historyWindow} timeline messages of the ticket, then the new user message.
 *
 * <p>Timeline mapping: TEXT from a person becomes a user message (prefixed with who wrote it when that is not the
 * caller's own side); an AI ENVELOPE becomes an assistant message of its text plus a compact summary of the blocks
 * it showed ({@code [shown r1 ORDER_LIST: …]}), never the full JSON; ACTION becomes a user line with
 * its outcome ("[user action] Confirmed: cancel order #50001 → EXECUTED (…)"); SYSTEM becomes a "[system note]"
 * system message. Internal notes are skipped for customers.
 */
@Slf4j
@Component
public class HistoryLoader {

    private final TicketMessageRepository messages;
    private final PromptRegistry prompts;
    private final AiProperties props;
    private final JsonMapper json;
    private final TransactionTemplate readTx;

    public HistoryLoader(TicketMessageRepository messages, PromptRegistry prompts, AiProperties props,
                         JsonMapper json, PlatformTransactionManager tm) {
        this.messages = messages;
        this.prompts = prompts;
        this.props = props;
        this.json = json;
        this.readTx = new TransactionTemplate(tm);
        this.readTx.setReadOnly(true);
    }

    public List<ChatMessage> initialMessages(TurnRuntime rt) {
        List<ChatMessage> out = base(rt);
        out.add(UserMessage.from(rt.userText()));
        return out;
    }

    /**
     * The model input of a turn started by a chat button: the "[user action] …" line as the user's turn (the same
     * line later turns see in history), then the structured event and its results as a system note.
     */
    public List<ChatMessage> actionMessages(TurnRuntime rt, String actionLine, String eventNote) {
        List<ChatMessage> out = base(rt);
        out.add(UserMessage.from(actionLine));
        out.add(SystemMessage.from(eventNote));
        return out;
    }

    private List<ChatMessage> base(TurnRuntime rt) {
        List<ChatMessage> out = new ArrayList<>();
        out.add(SystemMessage.from(prompts.supportAgent().text() + "\n\n## Caller\n" + rt.identityLine()
                + "\nToday is " + LocalDate.now(ZoneOffset.UTC) + "."));
        out.addAll(history(rt));
        return out;
    }

    List<ChatMessage> history(TurnRuntime rt) {
        int window = props.agent().historyWindow();
        if (window <= 0) return List.of();
        boolean customer = rt.ctx().isCustomer();
        PageRequest page = PageRequest.of(0, window + 1,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        List<ChatMessage> out = readTx.execute(status -> {
            var rows = customer
                    ? messages.findByTicketIdAndInternalFalse(rt.ticketId(), page).getContent()
                    : messages.findByTicketId(rt.ticketId(), page).getContent();
            List<ChatMessage> mapped = new ArrayList<>();
            rows.stream().filter(m -> !Objects.equals(m.getId(), rt.userMessageId())).limit(window)
                    .forEach(m -> {
                        ChatMessage c = toChat(m, rt);
                        if (c != null) mapped.add(c);
                    });
            return mapped;
        });
        Collections.reverse(out);
        return out;
    }

    private ChatMessage toChat(TicketMessage m, TurnRuntime rt) {
        String body = m.getBody() == null ? "" : m.getBody().strip();
        MessageKind kind = m.getKind() == null ? MessageKind.TEXT : m.getKind();
        return switch (kind) {
            case ENVELOPE -> AiMessage.from(nonBlank(withBlocks(body, m)));
            case ACTION -> UserMessage.from(ActionEvent.historyLine(nonBlank(body), field(m, "result"),
                    field(m, "detail")));
            case SYSTEM -> SystemMessage.from("[system note] " + nonBlank(body));
            case TEXT -> m.getAuthorType() == AuthorType.AI || m.getAuthorType() == null
                    ? AiMessage.from(nonBlank(body))
                    : m.getAuthorType() == AuthorType.SYSTEM
                    ? SystemMessage.from("[system note] " + nonBlank(body))
                    : UserMessage.from(prefix(m.getAuthorType(), rt) + nonBlank(body));
        };
    }

    /** Messages by the other side are marked, so the model knows a support agent (not the customer) wrote it. */
    private static String prefix(AuthorType author, TurnRuntime rt) {
        boolean own = rt.ctx().isCustomer() ? author == AuthorType.CUSTOMER : author == AuthorType.STAFF;
        if (own) return "";
        return switch (author) {
            case CUSTOMER -> "[customer wrote] ";
            case STAFF -> "[support agent wrote] ";
            case SELLER -> "[seller wrote] ";
            default -> "";
        };
    }

    private String withBlocks(String body, TicketMessage m) {
        if (m.getPayload() == null) return body;
        try {
            AgentResponse r = json.treeToValue(m.getPayload(), AgentResponse.class);
            String shown = Summaries.ofBlocks(r.blocks());
            return shown == null ? body : body + "\n" + shown;
        } catch (RuntimeException e) {
            log.warn("Envelope {} payload unreadable for history: {}", m.getId(), e.getMessage());
            return body;
        }
    }

    /** A text field of an ACTION payload, or null. */
    private static String field(TicketMessage m, String key) {
        if (m.getPayload() == null) return null;
        var v = m.getPayload().get(key);
        return v == null || v.isNull() ? null : v.asString();
    }

    private static String nonBlank(String s) {
        return s == null || s.isBlank() ? "(empty)" : s;
    }
}
