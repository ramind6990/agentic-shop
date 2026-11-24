package com.rmrx.agenticshop.ai.graph;

import com.rmrx.agenticshop.ai.action.ActionService;
import com.rmrx.agenticshop.ai.action.BlockFactory;
import com.rmrx.agenticshop.ai.config.AiProperties;
import com.rmrx.agenticshop.ai.contract.block.Block;
import com.rmrx.agenticshop.ai.contract.response.AgentResponse;
import com.rmrx.agenticshop.ai.contract.response.ResponseStatus;
import com.rmrx.agenticshop.ai.events.AgentEventPublisher;
import com.rmrx.agenticshop.ai.tools.BoundTools;
import com.rmrx.agenticshop.ai.tools.TurnResultRegistry;
import com.rmrx.agenticshop.ai.trace.TraceRecorder;
import com.rmrx.agenticshop.ai.trace.entity.AiRunStatus;
import com.rmrx.agenticshop.ai.usage.RunScopes;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.action.NodeActionWithConfig;
import org.bsc.langgraph4j.langchain4j.serializer.std.LC4jStateSerializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeActionWithConfig.node_async;

/**
 * The support agent as a LangGraph4j graph: a ReAct loop over the caller's tools, then compose and persist.
 *
 * <pre>
 * START → loadContext → agent ─┬─(tool calls, step &lt; budget)→ tools → agent
 *                              ├─(tool calls, budget used up)→ compose (status ERROR)
 *                              └─(final text)────────────────→ compose → persist → END
 * </pre>
 *
 * The graph is compiled once; per-turn objects travel in the run config as a {@link TurnRuntime}. Every node
 * re-binds the turn's RunScope, so model calls are billed to the run whatever thread LangGraph4j runs a node on.
 * A node failure propagates out of {@link #run}; the caller persists the error reply and fails the run.
 */
@Slf4j
@Component
public class SupportAgentGraph {

    static final String LOAD_CONTEXT = "loadContext";
    static final String AGENT = "agent";
    static final String TOOLS = "tools";
    static final String COMPOSE = "compose";
    static final String PERSIST = "persist";

    public static final String BUDGET_TEXT = "Sorry, I couldn't finish this request within my step limit. "
            + "Here is what I found so far; you can rephrase the question or ask for a human agent.";
    public static final String BUDGET_ERROR = "STEP_BUDGET_EXHAUSTED";
    private static final String EMPTY_TEXT = "Here is what I found.";
    private static final int ARGS_SUMMARY = 300;

    private final ObjectProvider<StreamingChatModel> streamingModel;
    private final HistoryLoader history;
    private final ComposeStep compose;
    private final BlockFactory blocks;
    private final ActionService actions;
    private final ReplyWriter replies;
    private final TraceRecorder trace;
    private final AiProperties props;
    private final JsonMapper json;
    private final CompiledGraph<AgentState> graph;

    public SupportAgentGraph(ObjectProvider<StreamingChatModel> streamingModel, HistoryLoader history,
                             ComposeStep compose, BlockFactory blocks, ActionService actions, ReplyWriter replies,
                             TraceRecorder trace, AiProperties props, JsonMapper json) throws GraphStateException {
        this.streamingModel = streamingModel;
        this.history = history;
        this.compose = compose;
        this.blocks = blocks;
        this.actions = actions;
        this.replies = replies;
        this.trace = trace;
        this.props = props;
        this.json = json;
        this.graph = build();
    }

    private CompiledGraph<AgentState> build() throws GraphStateException {
        StateGraph<AgentState> g = new StateGraph<>(AgentState.SCHEMA, new LC4jStateSerializer<>(AgentState::new))
                .addNode(LOAD_CONTEXT, node(LOAD_CONTEXT, this::loadContext))
                .addNode(AGENT, node(AGENT, this::agent))
                .addNode(TOOLS, node(TOOLS, this::tools))
                .addNode(COMPOSE, node(COMPOSE, this::compose))
                .addNode(PERSIST, node(PERSIST, this::persist))
                .addEdge(START, LOAD_CONTEXT)
                .addEdge(LOAD_CONTEXT, AGENT)
                .addConditionalEdges(AGENT, edge_async(SupportAgentGraph::route),
                        Map.of(TOOLS, TOOLS, COMPOSE, COMPOSE))
                .addEdge(TOOLS, AGENT)
                .addEdge(COMPOSE, PERSIST)
                .addEdge(PERSIST, END);
        // loadContext + up to budget agent steps + (budget-1) tool steps + compose + persist, with headroom
        int limit = props.agent().stepBudget() * 2 + 10;
        return g.compile(CompileConfig.builder().recursionLimit(limit).build());
    }

    /** Runs one turn to completion (reply persisted, FINAL emitted). Throws on failure. */
    public void run(TurnRuntime rt) {
        RunnableConfig config = RunnableConfig.builder()
                .threadId(rt.runId().toString())
                .putMetadata(AgentState.RUNTIME, rt)
                .build();
        graph.invoke(Map.of(), config);
        if (!rt.finished()) throw new IllegalStateException("graph ended without a persisted reply");
    }

    /** Wraps a node: checks the deadline before it starts and binds the turn's RunScope around it. */
    private static AsyncNodeActionWithConfig<AgentState> node(String name, NodeBody body) {
        NodeActionWithConfig<AgentState> action = (state, config) -> {
            TurnRuntime rt = AgentState.runtime(config);
            rt.checkDeadline("before " + name);
            return RunScopes.call(rt.scope(), () -> body.apply(state, rt));
        };
        return node_async(action);
    }

    @FunctionalInterface
    private interface NodeBody {
        Map<String, Object> apply(AgentState state, TurnRuntime rt) throws Exception;
    }

    // ------------------------------------------------------------------ nodes

    private Map<String, Object> loadContext(AgentState state, TurnRuntime rt) {
        return Map.of(AgentState.MESSAGES, history.initialMessages(rt), AgentState.STEP_COUNT, 0);
    }

    /** One streaming model call with the tools; text chunks go out as TOKEN_DELTA events. */
    private Map<String, Object> agent(AgentState state, TurnRuntime rt) throws Exception {
        StreamingChatModel model = streamingModel.getIfAvailable();
        if (model == null) throw new IllegalStateException("AI is disabled: no streaming chat model");
        AgentEventPublisher publisher = rt.publisher();
        ChatRequest request = ChatRequest.builder()
                .messages(state.messages())
                .toolSpecifications(rt.tools().specifications())
                .build();
        CompletableFuture<ChatResponse> done = new CompletableFuture<>();
        model.chat(request, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partial) {
                if (partial != null && !partial.isEmpty()) publisher.tokenDelta(partial);
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                done.complete(response);
            }

            @Override
            public void onError(Throwable error) {
                done.completeExceptionally(error);
            }
        });
        ChatResponse response;
        try {
            response = done.get(Math.max(1, rt.remaining().toMillis()), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new TurnTimeoutException("turn timed out waiting for the model");
        } catch (ExecutionException e) {
            throw e.getCause() instanceof Exception ex ? ex : e;
        }
        AiMessage ai = response.aiMessage();
        int steps = state.stepCount() + 1;
        Map<String, Object> out = new HashMap<>();
        out.put(AgentState.MESSAGES, List.of(ai));
        out.put(AgentState.STEP_COUNT, steps);
        if (ai.hasToolExecutionRequests()) {
            if (steps >= props.agent().stepBudget()) {
                log.warn("Run {} used its step budget ({}) with tool calls pending", rt.runId(), steps);
                out.put(AgentState.BUDGET_EXHAUSTED, true);
            }
        } else {
            out.put(AgentState.FINAL_TEXT, ai.text() == null ? "" : ai.text());
        }
        return out;
    }

    static String route(AgentState state) {
        boolean toolCalls = state.lastAiMessage().map(AiMessage::hasToolExecutionRequests).orElse(false);
        return toolCalls && !state.budgetExhausted() ? TOOLS : COMPOSE;
    }

    /** Executes the requested tool calls in order; results go back to the model as tool messages. */
    private Map<String, Object> tools(AgentState state, TurnRuntime rt) {
        AiMessage ai = state.lastAiMessage().orElseThrow();
        BoundTools tools = rt.tools();
        AgentEventPublisher publisher = rt.publisher();
        List<ChatMessage> results = new ArrayList<>();
        for (ToolExecutionRequest call : ai.toolExecutionRequests()) {
            rt.checkDeadline("before tool " + call.name());
            publisher.toolStarted(call.name(), tools.label(call.name()).orElse("Working on it…"));
            long t0 = System.nanoTime();
            String result = tools.execute(call);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            Outcome o = outcome(result, rt.registry());
            publisher.toolFinished(call.name(), o.ok(), o.errorCode(), ms);
            trace.recordToolStep(rt.runId(), call.name(), TraceRecorder.truncate(call.arguments(), ARGS_SUMMARY),
                    o.summary(), o.errorCode(), (int) ms);
            results.add(ToolExecutionResultMessage.from(call, result));
        }
        return Map.of(AgentState.MESSAGES, results);
    }

    private record Outcome(boolean ok, String errorCode, String summary) {
    }

    private Outcome outcome(String resultJson, TurnResultRegistry registry) {
        try {
            JsonNode node = json.readTree(resultJson);
            if ("OK".equals(node.path("type").asString(""))) {
                String id = node.path("resultId").asString("");
                String summary = registry.get(id).map(Summaries::ofEntry).orElse(id);
                return new Outcome(true, null, summary);
            }
            String code = node.path("code").asString("UNKNOWN");
            return new Outcome(false, code, code + ": " + node.path("message").asString(""));
        } catch (RuntimeException e) {
            return new Outcome(false, "UNPARSEABLE", TraceRecorder.truncate(resultJson, 200));
        }
    }

    /** Frames the reply (status, shown results, suggestions) and builds its blocks; the text is the agent's. */
    private Map<String, Object> compose(AgentState state, TurnRuntime rt) {
        TurnResultRegistry registry = rt.registry();
        ResponseStatus status;
        ComposeStep.Decision decision;
        String text;
        if (state.budgetExhausted()) {
            decision = compose.deterministic(registry);
            status = ResponseStatus.ERROR;
            text = BUDGET_TEXT;
        } else {
            String finalText = state.finalText().orElse("");
            decision = compose.decide(finalText, registry, rt);
            status = decision.status();
            text = finalText.isBlank() ? EMPTY_TEXT : finalText;
        }
        BlockFactory.BlockSet set = blocks.build(registry, decision.show(), rt.ctx());
        AgentResponse response = new AgentResponse(AgentResponse.CURRENT_SCHEMA_VERSION, status, text,
                set.blocks(), List.of(), decision.suggestedReplies(), rt.runId(), null, Instant.now());
        rt.composed(response, set.actionIds());
        log.info("Run {} composed: mode={} status={} show={} blocks={}", rt.runId(), decision.mode(), status,
                decision.show(), set.blocks().size());
        return Map.of();
    }

    /** Saves the ENVELOPE, ties the issued actions to it, closes the run and emits FINAL. */
    private Map<String, Object> persist(AgentState state, TurnRuntime rt) {
        AgentResponse saved = replies.save(rt.ticketId(), rt.response(), rt.internal());
        rt.persisted(saved, saved.messageId());
        actions.markMessage(rt.actionIds(), saved.messageId());
        boolean budget = state.budgetExhausted();
        trace.finishRun(rt.runId(), AiRunStatus.COMPLETED, budget ? BUDGET_ERROR : null,
                budget ? "step budget of " + props.agent().stepBudget() + " used up" : null);
        AgentEventPublisher publisher = rt.publisher();
        for (Block b : saved.blocks()) publisher.blockReady(b);
        publisher.complete(saved);
        rt.markFinished();
        return Map.of();
    }
}
