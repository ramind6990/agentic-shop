package com.rmrx.agenticshop.ai.graph.node;

import com.rmrx.agenticshop.ai.contract.interaction.InteractionJson;
import com.rmrx.agenticshop.ai.contract.reply.ReplyEnvelopes;
import com.rmrx.agenticshop.ai.contract.tool.ToolResult;
import com.rmrx.agenticshop.ai.graph.RunLimits;
import com.rmrx.agenticshop.ai.graph.agent.response.AgentResponse;
import com.rmrx.agenticshop.ai.graph.agent.response.DisplayReference;
import com.rmrx.agenticshop.ai.graph.agent.response.SuggestionReply;
import com.rmrx.agenticshop.ai.graph.agent.response.ResponseStatus;
import com.rmrx.agenticshop.ai.graph.guard.Guardrails;
import com.rmrx.agenticshop.ai.graph.guard.loop.Escalation;
import com.rmrx.agenticshop.ai.graph.guard.loop.LoopCheck;
import com.rmrx.agenticshop.ai.graph.guard.precheck.PrecheckOutcome;
import com.rmrx.agenticshop.ai.graph.state.RunState;
import com.rmrx.agenticshop.ai.graph.state.StepPlan;
import com.rmrx.agenticshop.ai.graph.state.ValidationOutcome;
import com.rmrx.agenticshop.ai.model.AiReplyProperties;
import com.rmrx.agenticshop.ai.tools.ToolCatalog;
import com.rmrx.agenticshop.ai.tools.ToolCategory;
import com.rmrx.agenticshop.ai.tools.ToolRegistry;
import com.rmrx.agenticshop.ai.trace.StepType;
import com.rmrx.agenticshop.ai.trace.TraceRecorder;
import com.rmrx.agenticshop.ai.trace.TraceStep;
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
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Validates the model's output: either a batch of tool calls (mixing rules, duplicate calls, per-step cap) or the
 * final {@code AgentResponse}. Fixes what it can in code; otherwise sends a specific error back to the model.
 * <p>
 * It applies the decisions of the guardrails ({@link Guardrails}) to the state: for tool calls the forced hand-off's
 * limits, the loop detector (blocked calls, escalation) and the prechecks of sensitive calls (blocked calls, approval
 * notes); for a final reply the forced hand-off and the output checks (grounding, links, reply audit).
 * <p>
 * Writes {@link RunState#VALIDATION_OUTCOME}, {@link RunState#VALIDATION_RETRIES} and {@link RunState#STEP_PLAN} on
 * every pass (so the routes never read a value from an earlier step), and {@link RunState#RESPONSE} when the run ends.
 * Every rejected output and every dropped tool call is written to the run's trace (a VALIDATION step).
 */
public class OutputValidationNode implements NodeActionWithConfig<RunState> {

    static final String SKIPPED = "Skipped: more than " + RunLimits.MAX_CALLS_PER_STEP + " tool calls in one step. "
            + "Call it again in a later step if you still need it.";

    private final ToolRegistry registry;
    private final AiReplyProperties reply;
    private final TraceRecorder trace;
    private final Guardrails guards;

    public OutputValidationNode(ToolRegistry registry, AiReplyProperties reply, TraceRecorder trace,
                                Guardrails guards) {
        this.reply = reply;
        this.registry = registry;
        this.trace = trace;
        this.guards = guards;
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
        if (!state.lastCallToolsOn()) {
            // the call was made with tools off (budget used up); tool calls now cannot be honoured
            String error = "Not executed: the tool budget of this run is used up. Give your final reply now.";
            return failure(state, errorResults(requests, error), "tool calls after the budget: " + names(requests));
        }
        ToolCatalog tools = registry.catalog(state.context());
        List<String> errors = checkToolCalls(tools, requests);
        guards.escalation().toolCallViolation(state, requests).ifPresent(errors::add);
        if (!errors.isEmpty()) {
            String error = String.join("; ", errors);
            return failure(state, errorResults(requests, error), error);
        }

        List<ToolExecutionResultMessage> results = new ArrayList<>(); // results for calls that will not run
        List<ToolExecutionRequest> kept = capStepCalls(requests, results);
        kept = dropDuplicateCalls(state, tools, kept, results);
        LoopCheck loop = guards.loop().inspect(state, tools, kept);
        if (loop.escalates()) return escalate(state, kept, results, loop.escalation());
        kept = dropBlocked(kept, loop.blocked(), results);
        traceDropped(state, results);
        StepPlan plan = classifyToolCalls(tools, kept);
        PrecheckOutcome prechecked = guards.prechecks().check(state.context(),
                state.lastToolRequests(plan.sensitiveCallIds()));
        plan = plan.withSensitiveCallIds(prechecked.allowedCallIds());
        results.addAll(prechecked.blockedResults());
        results.addAll(deferredResults(plan, kept));

        Map<String, Object> update = new HashMap<>();
        update.put(RunState.VALIDATION_OUTCOME, ValidationOutcome.TOOL_CALLS);
        update.put(RunState.VALIDATION_RETRIES, 0);
        update.put(RunState.STEP_PLAN, plan);
        if (!results.isEmpty()) update.put(RunState.MESSAGES, results);
        if (!prechecked.notes().isEmpty()) update.put(RunState.APPROVAL_NOTES, prechecked.notes());
        return update;
    }

    /** Calls the loop detector blocked get its "stop" result instead of running. */
    private static List<ToolExecutionRequest> dropBlocked(List<ToolExecutionRequest> requests,
                                                          Map<String, String> blocked,
                                                          List<ToolExecutionResultMessage> results) {
        if (blocked.isEmpty()) return requests;
        List<ToolExecutionRequest> kept = new ArrayList<>();
        for (ToolExecutionRequest r : requests) {
            if (blocked.containsKey(r.id())) {
                results.add(ToolExecutionResultMessage.builder().id(r.id()).toolName(r.name())
                        .text(blocked.get(r.id())).isError(false).build());
            } else {
                kept.add(r);
            }
        }
        return kept;
    }

    /**
     * The loop detector stopped the run: none of this step's calls runs, and the escalation is stored, so the next
     * model call is the forced hand-off (or the final reply without tools).
     */
    private Map<String, Object> escalate(RunState state, List<ToolExecutionRequest> calls,
                                         List<ToolExecutionResultMessage> results, Escalation escalation) {
        guards.escalation().traceStart(state, escalation);
        calls.forEach(c -> results.add(ToolExecutionResultMessage.from(c,
                "Not executed: this request is going in circles (" + escalation.reason() + ").")));
        return Map.of(
                RunState.VALIDATION_OUTCOME, ValidationOutcome.TOOL_CALLS,
                RunState.VALIDATION_RETRIES, 0,
                RunState.STEP_PLAN, StepPlan.empty(),
                RunState.MESSAGES, results,
                RunState.ESCALATION, escalation);
    }

    /** Unknown tool names, arguments that do not parse against the tool's parameters. */
    private List<String> checkToolCalls(ToolCatalog tools, List<ToolExecutionRequest> requests) {
        List<String> errors = new ArrayList<>();
        for (ToolExecutionRequest r : requests) {
            if (tools.find(r.name()).isEmpty()) errors.add("Unknown tool: " + r.name());
        }
        // TODO arguments parse against the tool's parameters; for ask tools: ask_choice has 2..6 options with unique
        //  values, questions are not blank
        return errors;
    }

    /**
     * Keeps the first {@link RunLimits#MAX_CALLS_PER_STEP} calls in the model's order; each other call gets its own
     * "skipped" result in {@code results}.
     */
    private List<ToolExecutionRequest> capStepCalls(List<ToolExecutionRequest> requests,
                                                    List<ToolExecutionResultMessage> results) {
        if (requests.size() <= RunLimits.MAX_CALLS_PER_STEP) return requests;
        requests.subList(RunLimits.MAX_CALLS_PER_STEP, requests.size())
                .forEach(r -> results.add(ToolExecutionResultMessage.from(r, SKIPPED)));
        return requests.subList(0, RunLimits.MAX_CALLS_PER_STEP);
    }

    /**
     * Removes calls with the same tool and arguments as an earlier call of this run (or of this step); each gets an
     * "already called" result in {@code results} pointing to the earlier result. Only calls after the run's last
     * sensitive call count: an action may have changed what a read returns.
     */
    private List<ToolExecutionRequest> dropDuplicateCalls(RunState state, ToolCatalog tools,
                                                          List<ToolExecutionRequest> requests,
                                                          List<ToolExecutionResultMessage> results) {
        Map<CallKey, String> earlier = earlierCalls(state, tools);
        List<ToolExecutionRequest> kept = new ArrayList<>();
        for (ToolExecutionRequest r : requests) {
            CallKey key = CallKey.of(r);
            if (earlier.containsKey(key)) {
                String ref = earlier.get(key);
                results.add(ToolExecutionResultMessage.from(r, "Not executed: already called with the same "
                        + "arguments in this request; "
                        + (ref == null ? "use its earlier result." : "use its result (ref " + ref + ").")));
            } else {
                earlier.put(key, null);
                kept.add(r);
            }
        }
        return kept;
    }

    /** Calls of earlier steps since the last step with a sensitive call, with the ref of their result (or null). */
    private Map<CallKey, String> earlierCalls(RunState state, ToolCatalog tools) {
        Map<String, String> refByCallId = state.toolResults().values().stream()
                .collect(Collectors.toMap(ToolResult::callId, ToolResult::ref, (a, b) -> a));
        List<ChatMessage> messages = state.messages();
        Map<CallKey, String> calls = new HashMap<>();
        // the last AI message is the step being validated
        for (int i = lastAiIndex(messages) - 1; i >= 0; i--) {
            if (!(messages.get(i) instanceof AiMessage ai) || !ai.hasToolExecutionRequests()) continue;
            if (ai.toolExecutionRequests().stream()
                    .anyMatch(r -> tools.category(r.name()).orElse(null) == ToolCategory.SENSITIVE)) break;
            ai.toolExecutionRequests().forEach(r -> calls.putIfAbsent(CallKey.of(r), refByCallId.get(r.id())));
        }
        return calls;
    }

    private static int lastAiIndex(List<ChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof AiMessage) return i;
        }
        return messages.size();
    }

    /** A tool call's identity for duplicate checks: its name and its arguments as a JSON tree (key order ignored). */
    private record CallKey(String name, Object arguments) {

        static CallKey of(ToolExecutionRequest r) {
            Object arguments;
            try {
                arguments = r.arguments() == null || r.arguments().isBlank()
                        ? "{}" : InteractionJson.tree(r.arguments());
            } catch (RuntimeException e) {
                arguments = r.arguments();
            }
            return new CallKey(r.name(), Objects.requireNonNullElse(arguments, "{}"));
        }
    }

    private void traceDropped(RunState state, List<ToolExecutionResultMessage> dropped) {
        if (dropped.isEmpty()) return;
        trace.step(state.context().runId(), TraceStep.of(StepType.VALIDATION, "tool calls dropped",
                dropped.stream().map(ToolExecutionResultMessage::toolName).collect(Collectors.joining(", ")),
                dropped.stream().map(ToolExecutionResultMessage::text).distinct().collect(Collectors.joining(" | "))));
    }

    /** Sorts the calls by tool category; sensitive calls are deferred when the step also asks the user something. */
    private StepPlan classifyToolCalls(ToolCatalog tools, List<ToolExecutionRequest> requests) {
        List<String> read = new ArrayList<>();
        List<String> ask = new ArrayList<>();
        List<String> sensitive = new ArrayList<>();
        for (ToolExecutionRequest r : requests) {
            switch (tools.category(r.name()).orElseThrow()) { // unknown tools were rejected before
                case READ -> read.add(r.id());
                case ASK -> ask.add(r.id());
                case SENSITIVE -> sensitive.add(r.id());
            }
        }
        if (!ask.isEmpty()) return new StepPlan(read, ask, List.of(), sensitive);
        return new StepPlan(read, ask, sensitive, List.of());
    }

    private List<ToolExecutionResultMessage> deferredResults(StepPlan plan, List<ToolExecutionRequest> requests) {
        return requests.stream()
                .filter(r -> plan.deferredCallIds().contains(r.id()))
                .map(r -> ToolExecutionResultMessage.from(r,
                        "Not executed: call it again after the user has answered your question."))
                .toList();
    }

    private List<ToolExecutionResultMessage> errorResults(List<ToolExecutionRequest> requests, String error) {
        return requests.stream().map(r -> ToolExecutionResultMessage.from(r, error)).toList();
    }

    private static String names(List<ToolExecutionRequest> requests) {
        return requests.stream().map(ToolExecutionRequest::name).collect(Collectors.joining(", "));
    }

    // ------------------------------------------------------------------ final answer

    private Map<String, Object> validateFinalAnswer(RunState state, String text) {
        Optional<AgentResponse> parsed = parseResponse(text);
        List<String> errors = new ArrayList<>();
        if (parsed.isEmpty()) {
            errors.add("The reply is not a valid AgentResponse JSON object.");
        } else {
            guards.escalation().finalAnswerViolation(state).ifPresent(errors::add);
            errors.addAll(checkSchema(parsed.get()));
            errors.addAll(checkTextLength(parsed.get()));
            errors.addAll(checkDisplayReference(state, parsed.get()));
            // output guardrails (grounding, links, reply audit) only for a reply that is otherwise valid
            if (errors.isEmpty()) errors.addAll(guards.output().check(state, parsed.get()));
        }
        if (!errors.isEmpty()) {
            String error = String.join("; ", errors);
            return failure(state, List.of(SystemMessage.from(
                    "Your reply was rejected: " + error + ". Send a corrected reply.")), error);
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
        List<String> errors = new ArrayList<>();
        if (response.status() == null) errors.add("status is missing (ANSWERED or REFUSED)");
        if (response.text() == null || response.text().isBlank()) errors.add("text is empty");
        List<SuggestionReply> suggestions = response.suggestedReplies() == null ? List.of() : response.suggestedReplies();
        if (suggestions.size() > reply.maxSuggestions()) {
            errors.add("at most " + reply.maxSuggestions() + " suggestedReplies, got " + suggestions.size());
        }
        for (SuggestionReply s : suggestions) {
            if (s == null || s.label() == null || s.label().isBlank() || s.text() == null || s.text().isBlank()) {
                errors.add("every suggested reply needs a label and a text");
            } else if (s.label().length() > reply.maxSuggestionLabelLength()) {
                errors.add("suggested reply label \"" + s.label() + "\" is longer than "
                        + reply.maxSuggestionLabelLength() + " characters");
            }
        }
        return errors;
    }

    private List<String> checkTextLength(AgentResponse response) {
        if (response.text() == null || response.text().length() <= reply.maxTextLength()) return List.of();
        return List.of("text is " + response.text().length() + " characters; keep it under "
                + reply.maxTextLength() + " and do not repeat the displayed data");
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
        if (!ReplyEnvelopes.displayable(result.value())) {
            return List.of(display.ref() + " (" + result.toolName() + ") cannot be shown to the user. Set "
                    + "finalReferenceToDisplay to null and write the key facts in text");
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

    /**
     * RETRY with feedback while retries and model calls are left; otherwise FAILED with the fallback response. A used
     * up token budget does not end the run here: the next call is simply made with tools off.
     */
    private Map<String, Object> failure(RunState state, List<? extends ChatMessage> feedback, String reason) {
        int retries = state.validationRetries() + 1;
        boolean canRetry = retries <= RunLimits.MAX_VALIDATION_RETRIES
                && state.modelCallCount() < guards.escalation().modelCallLimit(state);
        trace.step(state.context().runId(), TraceStep.of(StepType.VALIDATION, "rejected",
                "attempt " + retries + " of " + (RunLimits.MAX_VALIDATION_RETRIES + 1)
                        + (canRetry ? ", sent back to the model" : ", the run ends with the fallback reply"), reason));
        Map<String, Object> update = new HashMap<>();
        update.put(RunState.VALIDATION_OUTCOME, canRetry ? ValidationOutcome.RETRY : ValidationOutcome.FAILED);
        update.put(RunState.VALIDATION_RETRIES, retries);
        update.put(RunState.STEP_PLAN, StepPlan.empty());
        update.put(RunState.MESSAGES, feedback);
        if (!canRetry) update.put(RunState.RESPONSE, fallbackResponse());
        return update;
    }

    private AgentResponse fallbackResponse() {
        // TODO final wording
        return new AgentResponse(ResponseStatus.ANSWERED,
                "Sorry, I couldn't complete that request. Please try again or rephrase it.", null, List.of());
    }
}
