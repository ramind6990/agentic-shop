package com.rmrx.agenticshop.ai2.graph.node;

import com.rmrx.agenticshop.ai2.graph.RunLimits;
import com.rmrx.agenticshop.ai2.graph.agent.response.AgentResponse;
import com.rmrx.agenticshop.ai2.graph.state.RunState;
import com.rmrx.agenticshop.ai2.model.SystemPrompt;
import com.rmrx.agenticshop.ai2.tools.ToolRegistry;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.service.output.JsonSchemas;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.NodeActionWithConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Calls the model with the system prompt, the run's messages and the tools. The reply is either tool calls or the
 * final answer as {@link AgentResponse} JSON (the request's response format). When the run budget is exhausted, makes
 * the final call with tools turned off so the model answers from what it already gathered.
 */
public class ModelCallNode implements NodeActionWithConfig<RunState> {

    /** The final answer's shape, from the {@link AgentResponse} record and its {@code @Description}s. */
    static final ResponseFormat RESPONSE_FORMAT = ResponseFormat.builder()
            .type(ResponseFormatType.JSON)
            .jsonSchema(JsonSchemas.jsonSchemaFrom(AgentResponse.class)
                    .orElseThrow(() -> new IllegalStateException("no JSON schema for AgentResponse")))
            .build();

    private final ToolRegistry registry;
    private final ChatModel model;
    private final SystemPrompt systemPrompt;

    public ModelCallNode(ToolRegistry registry, ChatModel model, SystemPrompt systemPrompt) {
        this.registry = registry;
        this.model = model;
        this.systemPrompt = systemPrompt;
    }

    // TODO history provider (bean): load the projection of earlier runs here, when building the prompt
    //  (system prompt + history + RunState messages). History is not kept in RunState.
    //  - Summarization does NOT happen here: it runs after the graph finishes, when this run's projection is
    //    appended to the history, and the summary is persisted so loading stays a cheap read.
    //  - Loading on every call is only consistent because a conversation has at most one active run; that must be
    //    enforced persistently per conversation (not only by the UI).

    @Override
    public Map<String, Object> apply(RunState state, RunnableConfig config) {
        boolean toolsOn = toolsEnabled(state);
        List<ChatMessage> messages = new ArrayList<>();
        // rendered per call, never stored in MESSAGES (date, tools-off note)
        messages.add(SystemMessage.from(systemPrompt.render(state.context(), toolsOn)));
        messages.addAll(state.messages());

        ChatRequest request = ChatRequest.builder()
                .messages(messages)
                // always sent: the messages may contain earlier tool calls
                .toolSpecifications(registry.specificationsFor(state.context()))
                .toolChoice(toolsOn ? ToolChoice.AUTO : ToolChoice.NONE)
                .responseFormat(RESPONSE_FORMAT)
                .build();
        // TODO retry / back off on transient model errors (rate limits, timeouts); today the run fails
        AiMessage reply = model.chat(request).aiMessage();

        return Map.of(
                RunState.MESSAGES, List.of(reply),
                RunState.MODEL_CALL_COUNT, state.modelCallCount() + 1);
    }

    /** False for the last allowed model call of the run, which must answer without tools. */
    boolean toolsEnabled(RunState state) {
        return state.modelCallCount() + 1 < RunLimits.MAX_MODEL_CALLS;
    }

    // ------------------------------------------------------------------ MOCK model (commented out; kept for reference)
    // Scripted answers from the run's last message, used before the real model call existed:
    //  - user's message mentions shoes or products -> calls search_products
    //  - it mentions an order -> ask_choice (WE45 / WE51)
    //  - anything else -> a final answer echoing it
    //  - a search_products result -> a final answer displaying two of the products
    //  - an ask_choice answer -> a final answer naming the picked option
    // To use it again: call mockModel(state, toolsOn) instead of model.chat(request) and restore the imports
    // (InteractionJson, ToolResult, DisplayReference, ResponseStatus, SuggestionReply, AskUserTools, ChoiceOption,
    // ProductTools, ToolExecutionRequest, ToolExecutionResultMessage, UserMessage, Locale).
    //
    // private AiMessage mockModel(RunState state, boolean toolsOn) {
    //     List<ChatMessage> messages = state.messages();
    //     ChatMessage last = messages.isEmpty() ? null : messages.getLast();
    //
    //     if (last instanceof ToolExecutionResultMessage result) return mockAfterTool(state, result);
    //     if (last instanceof UserMessage user && toolsOn) {
    //         String text = user.singleText();
    //         String lower = text.toLowerCase(Locale.ROOT);
    //         if (lower.contains("shoe") || lower.contains("product")) {
    //             return toolCall(state, ProductTools.SEARCH_PRODUCTS, Map.of("query", text));
    //         }
    //         if (lower.contains("order")) {
    //             return toolCall(state, AskUserTools.ASK_CHOICE, Map.of(
    //                     "question", "Which order do you mean?",
    //                     "multiSelect", false,
    //                     "options", List.of(
    //                             new ChoiceOption("WE45", "Order #WE45", "2 items, shipped on 12 Nov"),
    //                             new ChoiceOption("WE51", "Order #WE51", "1 item, paid, not shipped yet"))));
    //         }
    //         return finalAnswer("You said: " + text, null, List.of());
    //     }
    //     return finalAnswer("Sorry, I could not work that out.", null, List.of());
    // }
    //
    // private AiMessage mockAfterTool(RunState state, ToolExecutionResultMessage result) {
    //     if (ProductTools.SEARCH_PRODUCTS.equals(result.toolName())) {
    //         String ref = state.toolResults().values().stream()
    //                 .filter(r -> r.callId().equals(result.id()))
    //                 .map(ToolResult::ref)
    //                 .findFirst().orElse(null);
    //         if (ref == null) return finalAnswer("The product search did not work: " + result.text(), null, List.of());
    //         return finalAnswer("Here are two running shoes that are in stock.",
    //                 new DisplayReference(ref, List.of("101", "102")),
    //                 List.of(new SuggestionReply("Show more", "show me more running shoes")));
    //     }
    //     if (AskUserTools.ASK_CHOICE.equals(result.toolName())) {
    //         Object selected = InteractionJson.read(result.text(), Map.class).get("selected");
    //         return selected == null
    //                 ? finalAnswer("OK, no order picked.", null, List.of())
    //                 : finalAnswer("Got it, you mean order " + String.join(", ", (List<String>) selected) + ".",
    //                         null, List.of());
    //     }
    //     return finalAnswer("Done: " + result.text(), null, List.of());
    // }
    //
    // private static AiMessage toolCall(RunState state, String tool, Map<String, Object> arguments) {
    //     return AiMessage.from(List.of(ToolExecutionRequest.builder()
    //             .id("mock-" + (state.modelCallCount() + 1))
    //             .name(tool)
    //             .arguments(InteractionJson.write(arguments))
    //             .build()));
    // }
    //
    // private static AiMessage finalAnswer(String text, DisplayReference display, List<SuggestionReply> suggestions) {
    //     return AiMessage.from(InteractionJson.write(
    //             new AgentResponse(ResponseStatus.ANSWERED, text, display, suggestions)));
    // }
}
