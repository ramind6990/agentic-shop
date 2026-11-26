package com.rmrx.agenticshop.ai2.graph.node;

import com.rmrx.agenticshop.ai2.contract.interaction.InteractionJson;
import com.rmrx.agenticshop.ai2.contract.tool.ToolResult;
import com.rmrx.agenticshop.ai2.graph.RunLimits;
import com.rmrx.agenticshop.ai2.graph.agent.response.AgentResponse;
import com.rmrx.agenticshop.ai2.graph.agent.response.DisplayReference;
import com.rmrx.agenticshop.ai2.graph.agent.response.ResponseStatus;
import com.rmrx.agenticshop.ai2.graph.state.RunState;
import com.rmrx.agenticshop.ai2.graph.state.StepPlan;
import com.rmrx.agenticshop.ai2.graph.state.ValidationOutcome;
import com.rmrx.agenticshop.ai2.tools.ToolCategory;
import com.rmrx.agenticshop.ai2.tools.ToolRegistry;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.NodeActionWithConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Validates the model's output: either a batch of tool calls (mixing rules, duplicate calls, per-step cap) or the
 * final {@code AgentResponse}. Fixes what it can in code; otherwise sends a specific error back to the model.
 * <p>
 * Writes {@link RunState#VALIDATION_OUTCOME}, {@link RunState#VALIDATION_RETRIES} and {@link RunState#STEP_PLAN} on
 * every pass (so the routes never read a value from an earlier step), and {@link RunState#RESPONSE} when the run ends.
 */
public class OutputValidationNode implements NodeActionWithConfig<RunState> {

    private final ToolRegistry registry;

    public OutputValidationNode(ToolRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Map<String, Object> apply(RunState state, RunnableConfig config) {
        Optional<AiMessage> ai = state.lastAiMessage().map(this::tryFixSilently);
        if (ai.isPresent() && ai.get().hasToolExecutionRequests()) return validateToolCalls(state, ai.get());
        return validateFinalAnswer(state, ai.map(AiMessage::text).orElse(null));
    }

    // ------------------------------------------------------------------ tool calls

    private Map<String, Object> validateToolCalls(RunState state, AiMessage ai) {
        List<ToolExecutionRequest> requests = ai.toolExecutionRequests();
        if (budgetExhausted(state)) {
            // the last call was made with tools off; tool calls now cannot be honoured
            return failure(state, errorResults(requests, "Not executed: the tool budget of this run is used up."));
        }
        List<String> errors = checkToolCalls(requests);
        if (!errors.isEmpty()) return failure(state, errorResults(requests, String.join("; ", errors)));

        List<ChatMessage> results = new ArrayList<>();          // results for calls that will not run
        List<ToolExecutionRequest> kept = capStepCalls(requests, results);
        kept = dropDuplicateCalls(state, kept, results);
        StepPlan plan = classifyToolCalls(kept);
        results.addAll(deferredResults(plan, kept));

        Map<String, Object> update = new HashMap<>();
        update.put(RunState.VALIDATION_OUTCOME, ValidationOutcome.TOOL_CALLS);
        update.put(RunState.VALIDATION_RETRIES, 0);
        update.put(RunState.STEP_PLAN, plan);
        if (!results.isEmpty()) update.put(RunState.MESSAGES, results);
        return update;
    }

    /** Unknown tool names, arguments that do not parse against the tool's parameters. */
    private List<String> checkToolCalls(List<ToolExecutionRequest> requests) {
        List<String> errors = new ArrayList<>();
        for (ToolExecutionRequest r : requests) {
            if (registry.find(r.name()).isEmpty()) errors.add("Unknown tool: " + r.name());
        }
        // TODO arguments parse against the tool's parameters; for ask tools: ask_choice has 2..6 options with unique
        //  values, questions are not blank
        return errors;
    }

    /**
     * Keeps the first {@link RunLimits#MAX_CALLS_PER_STEP} calls in the model's order; each other call gets its own
     * "skipped" result in {@code results}.
     */
    private List<ToolExecutionRequest> capStepCalls(List<ToolExecutionRequest> requests, List<ChatMessage> results) {
        // TODO cap; skipped calls: "Skipped: more than N calls in one step. Call it again in a later step if still
        //  needed."
        return requests;
    }

    /**
     * Removes calls with the same tool and arguments as a call earlier in this run; each gets an "already called,
     * result unchanged" result in {@code results}.
     */
    private List<ToolExecutionRequest> dropDuplicateCalls(RunState state, List<ToolExecutionRequest> requests,
                                                          List<ChatMessage> results) {
        // TODO compare with the tool calls of earlier AI messages in state.messages()
        return requests;
    }

    /** Sorts the calls by tool category; sensitive calls are deferred when the step also asks the user something. */
    private StepPlan classifyToolCalls(List<ToolExecutionRequest> requests) {
        List<String> read = new ArrayList<>();
        List<String> ask = new ArrayList<>();
        List<String> sensitive = new ArrayList<>();
        for (ToolExecutionRequest r : requests) {
            switch (categoryOf(r)) {
                case READ -> read.add(r.id());
                case ASK -> ask.add(r.id());
                case SENSITIVE -> sensitive.add(r.id());
            }
        }
        if (!ask.isEmpty()) return new StepPlan(read, ask, List.of(), sensitive);
        return new StepPlan(read, ask, sensitive, List.of());
    }

    /** Unknown tools were rejected by {@link #checkToolCalls} before classification. */
    private ToolCategory categoryOf(ToolExecutionRequest request) {
        return registry.category(request.name()).orElseThrow();
    }

    private List<ChatMessage> deferredResults(StepPlan plan, List<ToolExecutionRequest> requests) {
        return requests.stream()
                .filter(r -> plan.deferredCallIds().contains(r.id()))
                .<ChatMessage>map(r -> ToolExecutionResultMessage.from(r,
                        "Not executed: call it again after the user has answered your question."))
                .toList();
    }

    private List<ChatMessage> errorResults(List<ToolExecutionRequest> requests, String error) {
        return requests.stream().<ChatMessage>map(r -> ToolExecutionResultMessage.from(r, error)).toList();
    }

    // ------------------------------------------------------------------ final answer

    private Map<String, Object> validateFinalAnswer(RunState state, String text) {
        Optional<AgentResponse> parsed = parseResponse(text);
        List<String> errors = new ArrayList<>();
        if (parsed.isEmpty()) {
            errors.add("The reply is not a valid AgentResponse JSON object.");
        } else {
            errors.addAll(checkSchema(parsed.get()));
            errors.addAll(checkTextLength(parsed.get()));
            errors.addAll(checkDisplayReference(state, parsed.get()));
        }
        if (!errors.isEmpty()) {
            return failure(state, List.of(SystemMessage.from(
                    "Your reply was rejected: " + String.join("; ", errors) + ". Send a corrected reply.")));
        }
        return Map.of(
                RunState.VALIDATION_OUTCOME, ValidationOutcome.FINAL_ANSWER,
                RunState.VALIDATION_RETRIES, 0,
                RunState.STEP_PLAN, StepPlan.empty(),
                RunState.RESPONSE, parsed.get());
    }

    /** The model's final text as an {@link AgentResponse}; empty when it is missing or not that JSON. */
    private Optional<AgentResponse> parseResponse(String text) {
        if (text == null || text.isBlank()) return Optional.empty();
        try {
            return Optional.ofNullable(InteractionJson.read(text, AgentResponse.class));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /** Constraints the JSON schema cannot enforce or the model ignored: status, non-empty text, ≤3 suggestions. */
    private List<String> checkSchema(AgentResponse response) {
        // TODO
        return List.of();
    }

    private List<String> checkTextLength(AgentResponse response) {
        // TODO
        return List.of();
    }

    /** The displayed ref exists among this run's tool results, and its selected items belong to that result. */
    private List<String> checkDisplayReference(RunState state, AgentResponse response) {
        DisplayReference display = response.finalReferenceToDisplay();
        if (display == null) return List.of();
        Map<String, ToolResult> results = state.toolResults();
        ToolResult result = display.ref() == null ? null : results.get(display.ref());
        if (result == null) {
            return List.of("Unknown ref " + display.ref() + " in finalReferenceToDisplay; available refs: "
                    + new TreeSet<>(results.keySet()));
        }
        List<String> selected = display.selectedItemIdentifiers() == null ? List.of() : display.selectedItemIdentifiers();
        List<String> available = result.value().itemIdentifiers();
        List<String> unknown = selected.stream().filter(id -> !available.contains(id)).toList();
        if (!unknown.isEmpty()) {
            return List.of("selectedItemIdentifiers " + unknown + " are not items of " + display.ref()
                    + "; its items: " + available);
        }
        return List.of();
    }

    // ------------------------------------------------------------------ shared

    /** Fixes what code can fix without the model (e.g. dropping an unknown ref). */
    private AiMessage tryFixSilently(AiMessage ai) {
        // TODO
        return ai;
    }

    /** RETRY with feedback while retries and model calls are left; otherwise FAILED with the fallback response. */
    private Map<String, Object> failure(RunState state, List<ChatMessage> feedback) {
        int retries = state.validationRetries() + 1;
        boolean canRetry = retries <= RunLimits.MAX_VALIDATION_RETRIES && !budgetExhausted(state);
        Map<String, Object> update = new HashMap<>();
        update.put(RunState.VALIDATION_OUTCOME, canRetry ? ValidationOutcome.RETRY : ValidationOutcome.FAILED);
        update.put(RunState.VALIDATION_RETRIES, retries);
        update.put(RunState.STEP_PLAN, StepPlan.empty());
        update.put(RunState.MESSAGES, feedback);
        if (!canRetry) update.put(RunState.RESPONSE, fallbackResponse());
        return update;
    }

    private boolean budgetExhausted(RunState state) {
        return state.modelCallCount() >= RunLimits.MAX_MODEL_CALLS;
    }

    private AgentResponse fallbackResponse() {
        // TODO final wording
        return new AgentResponse(ResponseStatus.ANSWERED,
                "Sorry, I couldn't complete that request. Please try again or rephrase it.", null, List.of());
    }
}
