package com.rmrx.agenticshop.ai.graph.state;

import com.rmrx.agenticshop.ai.contract.interaction.HumanAnswer;
import com.rmrx.agenticshop.ai.contract.tool.ToolResult;
import com.rmrx.agenticshop.ai.graph.agent.response.AgentResponse;
import com.rmrx.agenticshop.ai.graph.guard.loop.Escalation;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Graph state of one run, checkpointed after every node. Every value must be {@link java.io.Serializable}.
 * <p>
 * Holds only this run's data. The history of earlier runs is not part of the state: it is loaded when the model is
 * called, and this run's messages are appended to it after the graph finishes.
 * <p>
 * Keys without a channel in {@link #SCHEMA} are last-write-wins.
 */
public class RunState extends AgentState {

    /** The run's {@link RunContext}; written once by the caller. */
    public static final String CONTEXT = "context";

    /** This run's messages (user message, model messages, tool results); append-only, duplicates kept. */
    public static final String MESSAGES = "messages";

    /** Model calls made in this run; written by the model call node. */
    public static final String MODEL_CALL_COUNT = "modelCallCount";

    /** Input + output tokens of this run's own model calls (the token budget); written by the model call node. */
    public static final String TOKENS_USED = "tokensUsed";

    /** Whether the last model call could call tools (false: the run's budget forced a final answer). */
    public static final String LAST_CALL_TOOLS_ON = "lastCallToolsOn";

    /** How the last model step ended ({@link ValidationOutcome}); written by the validation node on every pass. */
    public static final String VALIDATION_OUTCOME = "validationOutcome";

    /** Consecutive failed validations; written by the validation node on every pass (0 after a valid output). */
    public static final String VALIDATION_RETRIES = "validationRetries";

    /** The tool calls of the last model step by kind ({@link StepPlan}); written by the validation node on every pass. */
    public static final String STEP_PLAN = "stepPlan";

    /** The final {@link AgentResponse} (validated, or the fallback); written by the validation node when the run ends. */
    public static final String RESPONSE = "response";

    /**
     * The user's answers to questions and approvals, by tool call id ({@link HumanAnswer}). Written by the caller
     * through {@code updateState} before resuming; merged, because a replacing reducer would lose earlier answers.
     */
    public static final String HUMAN_ANSWERS = "humanAnswers";

    /**
     * The successful tool executions of this run, by ref ({@link ToolResult}); the model names one of them as the
     * result to display. Written by the tool execution nodes; merged.
     */
    public static final String TOOL_RESULTS = "toolResults";

    /**
     * Notes for the approval question of sensitive calls, by call id (e.g. what is left to refund); written by the
     * validation node from the prechecks; merged.
     */
    public static final String APPROVAL_NOTES = "approvalNotes";

    /** Set once the loop detector stopped the run ({@link Escalation}); written by the validation node. */
    public static final String ESCALATION = "escalation";

    private static final String REF_PREFIX = "r";

    public static final Map<String, Channel<?>> SCHEMA = Map.of(
            MESSAGES, Channels.appenderWithDuplicate(ArrayList::new),
            HUMAN_ANSWERS, Channels.<Map<String, HumanAnswer>>base(RunState::merge, LinkedHashMap::new),
            TOOL_RESULTS, Channels.<Map<String, ToolResult>>base(RunState::merge, LinkedHashMap::new),
            APPROVAL_NOTES, Channels.<Map<String, String>>base(RunState::merge, LinkedHashMap::new));

    private static <V> Map<String, V> merge(Map<String, V> current, Map<String, V> update) {
        Map<String, V> merged = new LinkedHashMap<>();
        if (current != null) merged.putAll(current);
        if (update != null) merged.putAll(update);
        return merged;
    }

    public RunState(Map<String, Object> initData) {
        super(initData);
    }

    public RunContext context() {
        return this.<RunContext>value(CONTEXT)
                .orElseThrow(() -> new IllegalStateException("no RunContext in the run state"));
    }

    public List<ChatMessage> messages() {
        return this.<List<ChatMessage>>value(MESSAGES).orElseGet(List::of);
    }

    public Optional<AiMessage> lastAiMessage() {
        List<ChatMessage> messages = messages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof AiMessage ai) return Optional.of(ai);
        }
        return Optional.empty();
    }

    /** The tool calls of the last AI message with the given ids, in the model's order. */
    public List<ToolExecutionRequest> lastToolRequests(Collection<String> callIds) {
        return lastAiMessage().map(AiMessage::toolExecutionRequests).orElseGet(List::of).stream()
                .filter(r -> callIds.contains(r.id()))
                .toList();
    }

    public int modelCallCount() {
        return this.<Integer>value(MODEL_CALL_COUNT).orElse(0);
    }

    public int tokensUsed() {
        return this.<Integer>value(TOKENS_USED).orElse(0);
    }

    public boolean lastCallToolsOn() {
        return this.<Boolean>value(LAST_CALL_TOOLS_ON).orElse(true);
    }

    public ValidationOutcome validationOutcome() {
        return this.<ValidationOutcome>value(VALIDATION_OUTCOME)
                .orElseThrow(() -> new IllegalStateException("no validation outcome in the run state"));
    }

    public int validationRetries() {
        return this.<Integer>value(VALIDATION_RETRIES).orElse(0);
    }

    public StepPlan stepPlan() {
        return this.<StepPlan>value(STEP_PLAN).orElseGet(StepPlan::empty);
    }

    public Optional<AgentResponse> response() {
        return value(RESPONSE);
    }

    public Map<String, HumanAnswer> humanAnswers() {
        return this.<Map<String, HumanAnswer>>value(HUMAN_ANSWERS).orElseGet(Map::of);
    }

    public Map<String, ToolResult> toolResults() {
        return this.<Map<String, ToolResult>>value(TOOL_RESULTS).orElseGet(Map::of);
    }

    public Map<String, String> approvalNotes() {
        return this.<Map<String, String>>value(APPROVAL_NOTES).orElseGet(Map::of);
    }

    public Optional<Escalation> escalation() {
        return value(ESCALATION);
    }

    /**
     * The ref for the {@code offset}-th (0-based) new result of the current node: {@code r1, r2, …} continuing after
     * the results already stored. Deterministic, so a node run again from its checkpoint gives the same refs.
     */
    public String nextResultRef(int offset) {
        return REF_PREFIX + (toolResults().size() + offset + 1);
    }
}
