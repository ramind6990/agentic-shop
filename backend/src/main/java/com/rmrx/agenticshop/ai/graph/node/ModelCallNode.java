package com.rmrx.agenticshop.ai.graph.node;

import com.rmrx.agenticshop.ai.graph.RunLimits;
import com.rmrx.agenticshop.ai.graph.agent.response.AgentResponse;
import com.rmrx.agenticshop.ai.graph.guard.loop.ForcedEscalation;
import com.rmrx.agenticshop.ai.graph.guard.loop.ModelTools;
import com.rmrx.agenticshop.ai.graph.state.RunState;
import com.rmrx.agenticshop.ai.memory.HistoryProvider;
import com.rmrx.agenticshop.ai.model.AiRunProperties;
import com.rmrx.agenticshop.ai.model.ModelCall;
import com.rmrx.agenticshop.ai.model.ModelGateway;
import com.rmrx.agenticshop.ai.model.SystemPrompt;
import com.rmrx.agenticshop.ai.tools.ToolRegistry;
import com.rmrx.agenticshop.ai.usage.TokenCounts;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.output.JsonSchemas;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.NodeActionWithConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Calls the model with the system prompt, the run's messages and the tools. The reply is either tool calls or the
 * final answer as {@link AgentResponse} JSON (the request's response format). When the run budget is used up (model
 * calls or tokens), makes the call with tools turned off so the model answers from what it already gathered.
 * <p>
 * The call goes through {@link ModelGateway}: transient errors are retried there (with progress notices), and every
 * attempt is traced and billed to the user.
 */
public class ModelCallNode implements NodeActionWithConfig<RunState> {

    /** The final answer's shape, from the {@link AgentResponse} record and its {@code @Description}s. */
    static final ResponseFormat RESPONSE_FORMAT = ResponseFormat.builder()
            .type(ResponseFormatType.JSON)
            .jsonSchema(JsonSchemas.jsonSchemaFrom(AgentResponse.class)
                    .orElseThrow(() -> new IllegalStateException("no JSON schema for AgentResponse")))
            .build();

    private final ToolRegistry registry;
    private final ModelGateway model;
    private final SystemPrompt systemPrompt;
    private final HistoryProvider historyProvider;
    private final AiRunProperties runProps;
    private final ForcedEscalation escalation;

    public ModelCallNode(ToolRegistry registry, ModelGateway model, SystemPrompt systemPrompt,
                         HistoryProvider historyProvider, AiRunProperties runProps, ForcedEscalation escalation) {
        this.registry = registry;
        this.model = model;
        this.systemPrompt = systemPrompt;
        this.historyProvider = historyProvider;
        this.runProps = runProps;
        this.escalation = escalation;
    }

    @Override
    public Map<String, Object> apply(RunState state, RunnableConfig config) {
        // a run the loop detector escalated gets the forced hand-off's tools and note instead
        ModelTools tools = escalation.modelTools(state, registry.catalog(state.context()), toolsEnabled(state));
        boolean toolsOn = tools.toolsOn();
        List<ChatMessage> messages = new ArrayList<>();
        // rendered per call, never stored in MESSAGES (date, tools-off note, hand-off note)
        String prompt = systemPrompt.render(state.context(), toolsOn);
        messages.add(SystemMessage.from(tools.note() == null ? prompt : prompt + "\n\n" + tools.note()));
        // earlier runs of the conversation (summary + newer messages; summarizes first when due); not in RunState.
        // Consistent across the run's model calls: a conversation has one active run, so nothing else writes it.
        messages.addAll(historyProvider.history(state.context()));
        messages.addAll(state.messages());

        ChatRequest request = ChatRequest.builder()
                .messages(messages)
                // always sent: the messages may contain earlier tool calls
                .toolSpecifications(tools.specifications())
                .toolChoice(tools.choice())
                .responseFormat(RESPONSE_FORMAT)
                .build();
        ChatResponse response = model.chat(request, ModelCall.chat(state.context()));

        return Map.of(
                RunState.MESSAGES, List.of(response.aiMessage()),
                RunState.MODEL_CALL_COUNT, state.modelCallCount() + 1,
                RunState.TOKENS_USED, state.tokensUsed() + TokenCounts.of(response.tokenUsage()).total(),
                RunState.LAST_CALL_TOOLS_ON, toolsOn);
    }

    /** False for the last allowed model call of the run, or once the run's tokens are used up: answer without tools. */
    boolean toolsEnabled(RunState state) {
        return state.modelCallCount() + 1 < RunLimits.MAX_MODEL_CALLS && state.tokensUsed() < runProps.maxTokens();
    }
}
