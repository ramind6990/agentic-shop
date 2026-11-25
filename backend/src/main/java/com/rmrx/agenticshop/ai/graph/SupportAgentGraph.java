package com.rmrx.agenticshop.ai.graph;

import com.rmrx.agenticshop.ai.action.ActionService;
import com.rmrx.agenticshop.ai.action.BlockFactory;
import com.rmrx.agenticshop.ai.config.AiProperties;
import com.rmrx.agenticshop.ai.contract.block.Block;
import com.rmrx.agenticshop.ai.contract.response.AgentResponse;
import com.rmrx.agenticshop.ai.contract.response.ResponseStatus;
import com.rmrx.agenticshop.ai.events.AgentEventPublisher;
import com.rmrx.agenticshop.ai.escalation.EscalationService;
import com.rmrx.agenticshop.ai.tools.BoundTools;
import com.rmrx.agenticshop.ai.tools.SupportTools;
import com.rmrx.agenticshop.ai.tools.ToolData;
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
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.action.NodeActionWithConfig;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
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
 * START → loadContext → agent ─┬─(tool calls, step &lt; budget)→ tools ─┬→ agent
 *                              │                                      └─(auto-escalated)→ compose
 *                              ├─(tool calls, budget used up)→ compose (customer: auto-escalate; staff: ERROR)
 *                              └─(final text)────────────────→ compose → persist → END
 * </pre>
 *
 * Escalation (handing the conversation to a person, see {@link EscalationService}) happens three ways: the model
 * calls {@code escalate_to_human}; a TALK_TO_HUMAN click escalates in loadContext; or code escalates a customer turn
 * that hit {@link #AUTO_ESCALATE_FAILURES} UNAVAILABLE/TIMEOUT tool failures or used up its step budget. A code-made
 * escalation is registered like a tool result (so the ESCALATION card comes from it) and traced as an
 * {@code escalation:<trigger>} step. Staff (internal) turns are never auto-escalated: the staff member is the human.
 *
 * A turn started by a chat button enters with an {@link ActionEvent} in the state: loadContext then makes the
 * event's deterministic tool calls in code ({@link ActionFollowUp}) and gives the model the event as a system note
 * instead of a user message; compose always shows the event's results (built from the registry, never model text).
 *
 * The graph is compiled once; per-turn objects travel in the run config as a {@link TurnRuntime}. Every node
 * re-binds the turn's RunScope, so model calls are billed to the run whatever thread LangGraph4j runs a node on.
 * A node failure propagates out of {@link #run}; the caller persists the error reply and fails the run.
 *
 * <p>With {@code app.ai.agent.checkpoints} the state is checkpointed after every node (thread = run id, see
 * {@link JdbcCheckpointSaver}); {@link #resume} continues a run a restart interrupted, when {@link #resumeBlocker}
 * allows it.
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
    /** Transient tool failures (UNAVAILABLE/TIMEOUT) in one customer turn that hand it to a person. */
    public static final int AUTO_ESCALATE_FAILURES = 2;
    public static final String FAILURE_ESCALATION_TEXT = "Sorry, I'm having trouble reaching our systems right now, "
            + "so I've handed your request to a support agent. They will follow up in this conversation.";
    public static final String BUDGET_ESCALATION_TEXT = "Sorry, I couldn't finish this request on my own, so I've "
            + "handed it to a support agent. They will follow up in this conversation.";
    private static final String ESCALATION_LABEL = "Handing over to a support agent\u2026";

    private final ObjectProvider<StreamingChatModel> streamingModel;
    private final HistoryLoader history;
    private final ComposeStep compose;
    private final BlockFactory blocks;
    private final ActionService actions;
    private final ReplyWriter replies;
    private final EscalationService escalations;
    private final TraceRecorder trace;
    private final AiProperties props;
    private final JsonMapper json;
    private final JdbcCheckpointSaver checkpoints;
    private final CompiledGraph<AgentState> graph;

    public SupportAgentGraph(ObjectProvider<StreamingChatModel> streamingModel, HistoryLoader history,
                             ComposeStep compose, BlockFactory blocks, ActionService actions, ReplyWriter replies,
                             EscalationService escalations, TraceRecorder trace, AiProperties props, JsonMapper json,
                             JdbcCheckpointSaver checkpoints)
            throws GraphStateException {
        this.checkpoints = checkpoints;
        this.escalations = escalations;
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
        StateGraph<AgentState> g = new StateGraph<>(AgentState.SCHEMA, checkpoints.serializer())
                .addNode(LOAD_CONTEXT, node(LOAD_CONTEXT, this::loadContext))
                .addNode(AGENT, node(AGENT, this::agent))
                .addNode(TOOLS, node(TOOLS, this::tools))
                .addNode(COMPOSE, node(COMPOSE, this::compose))
                .addNode(PERSIST, node(PERSIST, this::persist))
                .addEdge(START, LOAD_CONTEXT)
                .addEdge(LOAD_CONTEXT, AGENT)
                .addConditionalEdges(AGENT, edge_async(SupportAgentGraph::route),
                        Map.of(TOOLS, TOOLS, COMPOSE, COMPOSE))
                .addConditionalEdges(TOOLS, edge_async(SupportAgentGraph::afterTools),
                        Map.of(AGENT, AGENT, COMPOSE, COMPOSE))
                .addEdge(COMPOSE, PERSIST)
                .addEdge(PERSIST, END);
        // loadContext + up to budget agent steps + (budget-1) tool steps + compose + persist, with headroom
        int limit = props.agent().stepBudget() * 2 + 10;
        CompileConfig.Builder config = CompileConfig.builder().recursionLimit(limit);
        // a checkpoint after every node, thread = run id (see JdbcCheckpointSaver); TurnRunner deletes them when the
        // run ends, so only runs interrupted by a restart keep theirs
        // releaseThread(false): the graph keeps them at END; TurnRunner deletes them after the run (any outcome)
        if (props.agent().checkpoints()) config.checkpointSaver(checkpoints).releaseThread(false);
        return g.compile(config.build());
    }

    /** Runs one turn to completion (reply persisted, FINAL emitted). Throws on failure. */
    public void run(TurnRuntime rt) {
        Map<String, Object> input = rt.actionEvent() == null ? Map.of()
                : Map.of(AgentState.ACTION_EVENT, rt.actionEvent());
        graph.invoke(input, config(rt));
        if (!rt.finished()) throw new IllegalStateException("graph ended without a persisted reply");
    }

    /**
     * Continues an interrupted run from its newest checkpoint (thread = run id) with a rebuilt {@link TurnRuntime};
     * only call it when {@link #resumeBlocker} returned null for that checkpoint. Throws on failure.
     */
    public void resume(TurnRuntime rt) {
        graph.invoke(GraphInput.resume(), config(rt));
        if (!rt.finished()) throw new IllegalStateException("resumed graph ended without a persisted reply");
    }

    private static RunnableConfig config(TurnRuntime rt) {
        return RunnableConfig.builder()
                .threadId(rt.runId().toString())
                .putMetadata(AgentState.RUNTIME, rt)
                .build();
    }

    /**
     * Why a run cannot be resumed from this checkpoint, or null if it can. Resume is safe only while the turn has
     * produced nothing outside its graph state: the next node is {@code agent} or {@code tools} and no tool has
     * returned yet (no tool result message in the state; the history holds none) and the turn was not started by a
     * chat button (its {@link ActionEvent} registered results in the lost TurnResultRegistry). From there the rebuilt
     * runtime's empty registry is exact, and re-running the requested tool calls yields the same resultIds
     * (r1, r2, ... in call order). Later checkpoints depend on registry entries (resultIds the model saw, pending
     * confirmations with their tokens), which are not serializable, so such runs are failed instead.
     */
    public static String resumeBlocker(Checkpoint checkpoint) {
        if (checkpoint == null) return "no checkpoint";
        String next = checkpoint.getNextNodeId();
        if (!AGENT.equals(next) && !TOOLS.equals(next)) return "next node is " + next;
        AgentState state = new AgentState(checkpoint.getState());
        if (state.actionEvent().isPresent()) return "turn started by a chat button";
        if (state.messages().stream().anyMatch(ToolExecutionResultMessage.class::isInstance)) {
            return "tools already returned results";
        }
        if (state.autoEscalated().isPresent() || state.finalText().isPresent()) return "turn already decided";
        return null;
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
        ActionEvent event = state.actionEvent().orElse(null);
        if (event == null) {
            return Map.of(AgentState.MESSAGES, history.initialMessages(rt), AgentState.STEP_COUNT, 0);
        }
        // a chat button: code makes the deterministic calls (fresh read, prepare of a write button) before the model
        // runs; the model then only narrates (see ActionFollowUp)
        List<String> resultIds = new ArrayList<>(event.resultIds());
        List<String> lines = new ArrayList<>();
        for (String id : event.resultIds()) {
            rt.registry().get(id).ifPresent(e -> lines.add(Summaries.ofEntry(e)));
        }
        if (ActionFollowUp.escalates(event)) {
            String id = escalateInCode(rt, "The customer clicked \"Talk to a person\".",
                    EscalationService.Trigger.USER_BUTTON);
            if (id != null) {
                resultIds.add(id);
                rt.registry().get(id).ifPresent(e -> lines.add(Summaries.ofEntry(e)));
            } else {
                lines.add("the hand-off to a person failed; apologise and suggest trying again");
            }
        }
        for (ActionFollowUp.Call call : ActionFollowUp.calls(event, json)) {
            ToolCall done = runTool(rt, ToolExecutionRequest.builder().id("action-" + event.actionId())
                    .name(call.tool()).arguments(call.args()).build());
            if (done.outcome().ok()) {
                resultIds.add(done.resultId());
                lines.add(done.outcome().summary());
            } else {
                lines.add(call.tool() + " failed: " + done.outcome().summary());
            }
        }
        ActionEvent updated = event.withResultIds(resultIds);
        List<ChatMessage> messages = history.actionMessages(rt, updated.historyLine(),
                ActionFollowUp.note(updated, lines, json));
        return Map.of(AgentState.MESSAGES, messages, AgentState.STEP_COUNT, 0, AgentState.ACTION_EVENT, updated);
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

    static String afterTools(AgentState state) {
        return state.autoEscalated().isPresent() ? COMPOSE : AGENT;
    }

    /**
     * Executes the requested tool calls in order; results go back to the model as tool messages. A customer turn
     * with {@link #AUTO_ESCALATE_FAILURES} transient failures is escalated in code and goes straight to compose.
     */
    private Map<String, Object> tools(AgentState state, TurnRuntime rt) {
        AiMessage ai = state.lastAiMessage().orElseThrow();
        List<ChatMessage> results = new ArrayList<>();
        for (ToolExecutionRequest call : ai.toolExecutionRequests()) {
            results.add(ToolExecutionResultMessage.from(call, runTool(rt, call).result()));
        }
        Map<String, Object> out = new HashMap<>();
        out.put(AgentState.MESSAGES, results);
        if (!rt.internal() && rt.transientFailures() >= AUTO_ESCALATE_FAILURES && !escalated(rt.registry())) {
            String reason = "Automatic hand-off: our systems failed " + rt.transientFailures()
                    + " times while handling this request.";
            log.warn("Run {} auto-escalates after {} transient tool failures", rt.runId(), rt.transientFailures());
            if (escalateInCode(rt, reason, EscalationService.Trigger.AUTO_TOOL_FAILURES) != null) {
                out.put(AgentState.AUTO_ESCALATED, reason);
            }
        }
        return out;
    }

    private static boolean escalated(TurnResultRegistry registry) {
        return registry.all().stream().anyMatch(e -> e.data() instanceof ToolData.Escalated);
    }

    /**
     * Escalates the turn's ticket in code (a button or an automatic trigger), with the same progress events as a
     * tool call, a trace step {@code escalation:<trigger>} and the result registered under escalate_to_human, so the
     * ESCALATION card is built from it. Returns the resultId, or null if the escalation failed (logged and traced).
     */
    private String escalateInCode(TurnRuntime rt, String reason, EscalationService.Trigger trigger) {
        AgentEventPublisher publisher = rt.publisher();
        publisher.toolStarted(SupportTools.ESCALATE_TO_HUMAN, ESCALATION_LABEL);
        long t0 = System.nanoTime();
        String step = "escalation:" + trigger;
        try {
            EscalationService.Outcome o = escalations.escalate(rt.ctx(), reason, trigger, rt.runId());
            String id = rt.registry().register(SupportTools.ESCALATE_TO_HUMAN, SupportTools.toData(o));
            int ms = (int) ((System.nanoTime() - t0) / 1_000_000);
            publisher.toolFinished(SupportTools.ESCALATE_TO_HUMAN, true, null, ms);
            trace.recordToolStep(rt.runId(), step, TraceRecorder.truncate(reason, ARGS_SUMMARY),
                    rt.registry().get(id).map(Summaries::ofEntry).orElse(id)
                            + "; " + o.supersededActions() + " open actions superseded", null, ms);
            return id;
        } catch (RuntimeException e) {
            int ms = (int) ((System.nanoTime() - t0) / 1_000_000);
            log.error("Run {}: escalation ({}) failed", rt.runId(), trigger, e);
            publisher.toolFinished(SupportTools.ESCALATE_TO_HUMAN, false, "ESCALATION_FAILED", ms);
            trace.recordToolStep(rt.runId(), step, TraceRecorder.truncate(reason, ARGS_SUMMARY),
                    TraceRecorder.truncate(String.valueOf(e.getMessage()), 200), "ESCALATION_FAILED", ms);
            return null;
        }
    }

    /** One tool call with its progress events and trace step; used for model calls and code-made calls alike. */
    private ToolCall runTool(TurnRuntime rt, ToolExecutionRequest call) {
        BoundTools tools = rt.tools();
        AgentEventPublisher publisher = rt.publisher();
        rt.checkDeadline("before tool " + call.name());
        publisher.toolStarted(call.name(), tools.label(call.name()).orElse("Working on it…"));
        long t0 = System.nanoTime();
        String result = tools.execute(call);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        Outcome o = outcome(result, rt.registry());
        if ("UNAVAILABLE".equals(o.errorCode()) || "TIMEOUT".equals(o.errorCode())) rt.recordTransientFailure();
        publisher.toolFinished(call.name(), o.ok(), o.errorCode(), ms);
        trace.recordToolStep(rt.runId(), call.name(), TraceRecorder.truncate(call.arguments(), ARGS_SUMMARY),
                o.summary(), o.errorCode(), (int) ms);
        return new ToolCall(result, o, o.ok() ? resultIdOf(result) : null);
    }

    private record ToolCall(String result, Outcome outcome, String resultId) {
    }

    private record Outcome(boolean ok, String errorCode, String summary) {
    }

    private String resultIdOf(String resultJson) {
        try {
            return json.readTree(resultJson).path("resultId").asString(null);
        } catch (RuntimeException e) {
            return null;
        }
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
        if (state.autoEscalated().isPresent()) {
            decision = compose.deterministic(registry);
            status = decision.status();
            text = FAILURE_ESCALATION_TEXT;
        } else if (state.budgetExhausted()) {
            boolean handedOff = !rt.internal() && (escalated(registry) || escalateInCode(rt,
                    "Automatic hand-off: the assistant could not finish this request within its step limit.",
                    EscalationService.Trigger.AUTO_STEP_BUDGET) != null);
            decision = compose.deterministic(registry);
            status = handedOff ? decision.status() : ResponseStatus.ERROR;
            text = handedOff ? BUDGET_ESCALATION_TEXT : BUDGET_TEXT;
        } else {
            String finalText = state.finalText().orElse("");
            decision = compose.decide(finalText, registry, rt);
            ActionEvent event = state.actionEvent().orElse(null);
            if (event != null) decision = ActionFollowUp.frame(decision, event, registry);
            status = decision.status();
            text = finalText.isBlank() ? EMPTY_TEXT : finalText;
        }
        // once handed to a person, the reply offers no further buttons (and the ticket's open ones were superseded)
        BlockFactory.BlockSet set = blocks.build(registry, decision.show(), rt.ctx(),
                status != ResponseStatus.ESCALATED);
        AgentResponse response = new AgentResponse(AgentResponse.CURRENT_SCHEMA_VERSION, status, text,
                set.blocks(), set.citations(), decision.suggestedReplies(), set.suggestions(), rt.runId(), null,
                Instant.now());
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
