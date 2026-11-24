package com.rmrx.agenticshop.ai.graph;

import com.rmrx.agenticshop.ai.config.AiProperties;
import com.rmrx.agenticshop.ai.config.AiProperties.ComposeMode;
import com.rmrx.agenticshop.ai.contract.response.ResponseStatus;
import com.rmrx.agenticshop.ai.contract.response.SuggestedReply;
import com.rmrx.agenticshop.ai.tools.ToolData;
import com.rmrx.agenticshop.ai.tools.TurnResultRegistry;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import com.rmrx.agenticshop.ai.usage.RunScopes;
import com.rmrx.agenticshop.ai.usage.entity.AiUsagePurpose;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decides the frame of a reply after the ReAct loop: its status, which registered results the UI shows, and quick
 * replies. The text itself is never generated here; it is the agent's final text.
 *
 * <p>{@code llm} mode makes one non-streaming structured-output call (OpenAI JSON schema) with the final text and a
 * compact list of the turn's results (billed as SUMMARY), then validates the answer: unknown resultIds are
 * dropped, at most {@link #MAX_SUGGESTIONS} suggestions are kept. Any failure (model error, bad JSON, little time left) falls back
 * to {@code deterministic}: every result of the turn except superseded duplicates, ANSWERED, no suggestions. In both
 * modes a write proposed this turn (a {@link ToolData.ConfirmationPending}) forces NEEDS_CONFIRMATION and is always
 * shown; see {@link #enforce} for all rules (escalation, confirmation, clarification, refusal).
 */
@Slf4j
@Component
public class ComposeStep {

    public static final int MAX_SUGGESTIONS = 3;
    private static final int MAX_LABEL = 40;
    private static final int MAX_SUGGESTION_TEXT = 200;
    /** Below this time left, the llm call is skipped so the turn can still finish. */
    private static final Duration MIN_TIME_FOR_LLM = Duration.ofSeconds(5);
    /** The statuses the compose model may choose; ESCALATED needs an escalation tool, ERROR is set by the graph. */
    private static final List<String> STATUSES = List.of(ResponseStatus.ANSWERED.name(),
            ResponseStatus.NEEDS_INFO.name(), ResponseStatus.NEEDS_CONFIRMATION.name(), ResponseStatus.REFUSED.name());

    private static final String INSTRUCTIONS = """
            You frame the final reply of a shop support assistant for the chat UI. You get the assistant's final \
            text and the tool results of this turn (resultId, tool, one-line summary). Return JSON only:
            - status: ANSWERED (the request was handled or answered), NEEDS_INFO (the text asks the user a \
            clarifying question), NEEDS_CONFIRMATION (a change was proposed and awaits the user's confirmation), \
            REFUSED (the request was declined, e.g. off-topic).
            - show: the resultIds whose cards help the user with this reply, in display order. Leave out results \
            the text does not rely on and older duplicates. Use only resultIds from the list; [] if none.
            - suggestedReplies: 0 to 3 short follow-ups the user is likely to send next, each with a label (max 4 \
            words) and the text sent as the user's message. Never suggest confirming a change; the UI has buttons \
            for that.""";

    /** The validated decision. */
    public record Decision(ResponseStatus status, List<String> show, List<SuggestedReply> suggestedReplies,
                           ComposeMode mode) {
    }

    private final ObjectProvider<ChatModel> chatModel;
    private final JsonMapper json;
    private volatile ComposeMode mode;

    public ComposeStep(ObjectProvider<ChatModel> chatModel, JsonMapper json, AiProperties props) {
        this.chatModel = chatModel;
        this.json = json;
        this.mode = props.agent().composeMode();
    }

    public ComposeMode mode() {
        return mode;
    }

    /** Dev only: switches the compose mode at runtime. */
    public void overrideMode(ComposeMode mode) {
        log.warn("Compose mode changed at runtime: {} -> {}", this.mode, mode);
        this.mode = Objects.requireNonNull(mode);
    }

    public Decision decide(String finalText, TurnResultRegistry registry, TurnRuntime rt) {
        if (mode == ComposeMode.LLM) {
            try {
                if (rt.remaining().compareTo(MIN_TIME_FOR_LLM) < 0) throw new IllegalStateException("too little time");
                return enforce(llm(finalText, registry, rt), registry);
            } catch (RuntimeException e) {
                log.warn("LLM compose failed for run {} ({}); using deterministic", rt.runId(), e.toString());
            }
        }
        return deterministic(registry);
    }

    /** Every result of the turn except superseded duplicates; ANSWERED unless a write awaits confirmation. */
    public Decision deterministic(TurnResultRegistry registry) {
        return enforce(new Decision(ResponseStatus.ANSWERED, latestDistinct(registry), List.of(),
                ComposeMode.DETERMINISTIC), registry);
    }

    // ------------------------------------------------------------------ llm

    private Decision llm(String finalText, TurnResultRegistry registry, TurnRuntime rt) {
        ChatModel model = chatModel.getIfAvailable();
        if (model == null) throw new IllegalStateException("no chat model");
        String results = registry.all().isEmpty() ? "(none)"
                : registry.all().stream().map(Summaries::ofEntry).collect(Collectors.joining("\n"));
        ChatRequest request = ChatRequest.builder()
                .messages(SystemMessage.from(INSTRUCTIONS),
                        UserMessage.from("Final assistant text:\n" + (finalText == null ? "" : finalText)
                                + "\n\nTool results of this turn:\n" + results))
                .responseFormat(ResponseFormat.builder().type(ResponseFormatType.JSON).jsonSchema(schema()).build())
                .build();
        // billed as SUMMARY: it is not part of the conversation, so the ticket's "current context" (its latest
        // CHAT call) keeps meaning the agent's prompt size
        ChatResponse response = RunScopes.get(rt.scope().withPurpose(AiUsagePurpose.SUMMARY),
                () -> model.chat(request));
        String raw = response.aiMessage().text();
        if (raw == null || raw.isBlank()) throw new IllegalStateException("empty compose output");
        JsonNode node = json.readTree(raw);

        ResponseStatus status = ResponseStatus.valueOf(node.path("status").asString(""));
        if (!STATUSES.contains(status.name())) throw new IllegalStateException("status " + status + " not allowed");
        Set<String> known = registry.all().stream().map(TurnResultRegistry.Entry::resultId)
                .collect(Collectors.toSet());
        LinkedHashSet<String> show = new LinkedHashSet<>();
        for (JsonNode id : node.path("show")) {
            String s = id.asString("");
            if (known.contains(s)) show.add(s);
            else log.info("Compose returned unknown resultId '{}'; dropped", s);
        }
        List<SuggestedReply> replies = new ArrayList<>();
        for (JsonNode r : node.path("suggestedReplies")) {
            String label = r.path("label").asString("").strip();
            String text = r.path("text").asString("").strip();
            if (label.isEmpty() || text.isEmpty() || replies.size() >= MAX_SUGGESTIONS) continue;
            replies.add(new SuggestedReply(clip(label, MAX_LABEL), clip(text, MAX_SUGGESTION_TEXT)));
        }
        return new Decision(status, List.copyOf(show), replies, ComposeMode.LLM);
    }

    private static JsonSchema schema() {
        JsonObjectSchema reply = JsonObjectSchema.builder()
                .addStringProperty("label")
                .addStringProperty("text")
                .required("label", "text")
                .additionalProperties(false)
                .build();
        JsonObjectSchema root = JsonObjectSchema.builder()
                .addProperty("status", JsonEnumSchema.builder().enumValues(STATUSES).build())
                .addProperty("show", JsonArraySchema.builder().items(new JsonStringSchema()).build())
                .addProperty("suggestedReplies", JsonArraySchema.builder().items(reply).build())
                .required("status", "show", "suggestedReplies")
                .additionalProperties(false)
                .build();
        return JsonSchema.builder().name("reply_frame").rootElement(root).build();
    }

    // ------------------------------------------------------------------ rules shared by both modes

    /**
     * Rules over the model's frame, in priority order:
     * <ol>
     * <li>The conversation was escalated this turn: ESCALATED, only the escalation card, no quick replies.</li>
     * <li>A write was proposed this turn: NEEDS_CONFIRMATION, and its (latest) card is always shown.</li>
     * <li>The user was asked to choose (ask_user_to_choose_order): NEEDS_INFO, and the choice card is shown.</li>
     * <li>REFUSED shows no cards (whatever the model listed); the reply keeps only the reply-level TALK_TO_HUMAN.</li>
     * </ol>
     */
    static Decision enforce(Decision d, TurnResultRegistry registry) {
        List<String> latest = latestDistinct(registry);
        List<String> escalations = ids(registry, ToolData.Escalated.class);
        if (!escalations.isEmpty()) {
            return new Decision(ResponseStatus.ESCALATED, List.of(escalations.getLast()), List.of(), d.mode());
        }
        List<String> pending = ids(registry, ToolData.ConfirmationPending.class);
        if (!pending.isEmpty()) {
            // only the latest proposal per operation and target is still OPEN; earlier ones were superseded
            List<String> latestPending = latest.stream().filter(pending::contains).toList();
            LinkedHashSet<String> show = new LinkedHashSet<>(d.show());
            show.addAll(latestPending);
            return new Decision(ResponseStatus.NEEDS_CONFIRMATION, List.copyOf(show), d.suggestedReplies(), d.mode());
        }
        List<String> choices = ids(registry, ToolData.ChoicePending.class);
        if (!choices.isEmpty()) {
            LinkedHashSet<String> show = new LinkedHashSet<>(d.show());
            show.removeAll(choices);
            show.add(choices.getLast());
            return new Decision(ResponseStatus.NEEDS_INFO, List.copyOf(show), d.suggestedReplies(), d.mode());
        }
        if (d.status() == ResponseStatus.REFUSED && !d.show().isEmpty()) {
            return new Decision(ResponseStatus.REFUSED, List.of(), d.suggestedReplies(), d.mode());
        }
        return d;
    }

    private static List<String> ids(TurnResultRegistry registry, Class<?> type) {
        return registry.all().stream().filter(e -> type.isInstance(e.data()))
                .map(TurnResultRegistry.Entry::resultId).toList();
    }

    /**
     * resultIds in registration order, dropping an entry when a later one supersedes it: the same tool with equal
     * data (a repeated lookup), or a later proposal of the same operation on the same target.
     */
    static List<String> latestDistinct(TurnResultRegistry registry) {
        List<TurnResultRegistry.Entry> all = registry.all();
        List<String> out = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            TurnResultRegistry.Entry e = all.get(i);
            boolean superseded = false;
            for (int j = i + 1; j < all.size() && !superseded; j++) {
                superseded = supersedes(all.get(j), e);
            }
            if (!superseded) out.add(e.resultId());
        }
        return out;
    }

    private static boolean supersedes(TurnResultRegistry.Entry later, TurnResultRegistry.Entry earlier) {
        if (later.data() instanceof ToolData.ConfirmationPending a
                && earlier.data() instanceof ToolData.ConfirmationPending b) {
            return a.operation() == b.operation() && Objects.equals(a.target(), b.target());
        }
        return later.toolName().equals(earlier.toolName()) && Objects.equals(later.data(), earlier.data());
    }

    private static String clip(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
