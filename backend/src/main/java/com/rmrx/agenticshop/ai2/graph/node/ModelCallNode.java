package com.rmrx.agenticshop.ai2.graph.node;

import com.rmrx.agenticshop.ai2.contract.interaction.InteractionJson;
import com.rmrx.agenticshop.ai2.contract.tool.ToolResult;
import com.rmrx.agenticshop.ai2.graph.RunLimits;
import com.rmrx.agenticshop.ai2.graph.agent.response.AgentResponse;
import com.rmrx.agenticshop.ai2.graph.agent.response.DisplayReference;
import com.rmrx.agenticshop.ai2.graph.agent.response.ResponseStatus;
import com.rmrx.agenticshop.ai2.graph.agent.response.SuggestionReply;
import com.rmrx.agenticshop.ai2.graph.state.RunState;
import com.rmrx.agenticshop.ai2.tools.ToolRegistry;
import com.rmrx.agenticshop.ai2.tools.ask.AskUserTools;
import com.rmrx.agenticshop.ai2.tools.ask.ChoiceOption;
import com.rmrx.agenticshop.ai2.tools.product.ProductTools;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.NodeActionWithConfig;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Calls the model with the run's messages and tools. When the run budget is exhausted, makes the final call
 * with tools turned off so the model answers from what it already gathered.
 * <p>
 * For now the model is a MOCK (see the section at the bottom): a script that answers from the run's last message, so
 * the graph, the tools and the conversation endpoints can be tried without a model.
 */
public class ModelCallNode implements NodeActionWithConfig<RunState> {

    private final ToolRegistry registry;

    public ModelCallNode(ToolRegistry registry) {
        this.registry = registry;
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
        // TODO build the ChatRequest:
        //  - system prompt rendered for this call; when !toolsOn add a per-call note ("Tool budget reached; answer now
        //    with what you have, no more tool calls") that is NOT written to MESSAGES
        //  - history (provider) + state.messages()
        //  - tool specifications always: registry.specificationsFor(state.context()) (the history contains tool
        //    calls); toolChoice(ToolChoice.NONE) when !toolsOn
        //  - response format: AgentResponse JSON schema
        // TODO call the model instead of the mock
        AiMessage reply = mockModel(state, toolsOn);
        return Map.of(
                RunState.MESSAGES, List.of(reply),
                RunState.MODEL_CALL_COUNT, state.modelCallCount() + 1);
    }

    /** False for the last allowed model call of the run, which must answer without tools. */
    boolean toolsEnabled(RunState state) {
        return state.modelCallCount() + 1 < RunLimits.MAX_MODEL_CALLS;
    }

    // ------------------------------------------------------------------ MOCK model (remove with the real model call)

    /**
     * Answers from the run's last message:
     * <ul>
     * <li>the user's message mentions shoes or products → calls {@code search_products};</li>
     * <li>it mentions an order → asks which order ({@code ask_choice});</li>
     * <li>anything else → a final answer echoing it;</li>
     * <li>a {@code search_products} result → a final answer displaying two of the products;</li>
     * <li>an {@code ask_choice} answer → a final answer naming the picked option.</li>
     * </ul>
     */
    private AiMessage mockModel(RunState state, boolean toolsOn) {
        List<ChatMessage> messages = state.messages();
        ChatMessage last = messages.isEmpty() ? null : messages.getLast();

        if (last instanceof ToolExecutionResultMessage result) return mockAfterTool(state, result);
        if (last instanceof UserMessage user && toolsOn) {
            String text = user.singleText();
            String lower = text.toLowerCase(Locale.ROOT);
            if (lower.contains("shoe") || lower.contains("product")) {
                return toolCall(state, ProductTools.SEARCH_PRODUCTS, Map.of("query", text));
            }
            if (lower.contains("order")) {
                return toolCall(state, AskUserTools.ASK_CHOICE, Map.of(
                        "question", "Which order do you mean?",
                        "multiSelect", false,
                        "options", List.of(
                                new ChoiceOption("WE45", "Order #WE45", "2 items, shipped on 12 Nov"),
                                new ChoiceOption("WE51", "Order #WE51", "1 item, paid, not shipped yet"))));
            }
            return finalAnswer("You said: " + text, null, List.of());
        }
        return finalAnswer("Sorry, I could not work that out.", null, List.of());
    }

    private AiMessage mockAfterTool(RunState state, ToolExecutionResultMessage result) {
        if (ProductTools.SEARCH_PRODUCTS.equals(result.toolName())) {
            String ref = state.toolResults().values().stream()
                    .filter(r -> r.callId().equals(result.id()))
                    .map(ToolResult::ref)
                    .findFirst().orElse(null);
            if (ref == null) return finalAnswer("The product search did not work: " + result.text(), null, List.of());
            return finalAnswer("Here are two running shoes that are in stock.",
                    new DisplayReference(ref, List.of("101", "102")),
                    List.of(new SuggestionReply("Show more", "show me more running shoes")));
        }
        if (AskUserTools.ASK_CHOICE.equals(result.toolName())) {
            Object selected = InteractionJson.read(result.text(), Map.class).get("selected");
            return selected == null
                    ? finalAnswer("OK, no order picked.", null, List.of())
                    : finalAnswer("Got it, you mean order " + String.join(", ", (List<String>) selected) + ".",
                            null, List.of());
        }
        return finalAnswer("Done: " + result.text(), null, List.of());
    }

    private static AiMessage toolCall(RunState state, String tool, Map<String, Object> arguments) {
        return AiMessage.from(List.of(ToolExecutionRequest.builder()
                .id("mock-" + (state.modelCallCount() + 1))
                .name(tool)
                .arguments(InteractionJson.write(arguments))
                .build()));
    }

    private static AiMessage finalAnswer(String text, DisplayReference display, List<SuggestionReply> suggestions) {
        return AiMessage.from(InteractionJson.write(
                new AgentResponse(ResponseStatus.ANSWERED, text, display, suggestions)));
    }
}
