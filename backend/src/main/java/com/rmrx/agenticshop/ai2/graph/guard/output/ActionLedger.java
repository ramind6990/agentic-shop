package com.rmrx.agenticshop.ai2.graph.guard.output;

import com.rmrx.agenticshop.ai2.contract.interaction.HumanAnswer;
import com.rmrx.agenticshop.ai2.graph.state.RunState;
import com.rmrx.agenticshop.ai2.tools.ToolCatalog;
import com.rmrx.agenticshop.ai2.tools.ToolCategory;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The real outcome of every action (sensitive tool call) the model attempted in this run, read from the state: what a
 * reply may claim happened, and what it may not.
 */
public record ActionLedger(List<Entry> entries) {

    public enum Outcome {
        /** Executed; its result is stored under a ref. */
        SUCCEEDED,
        /** Executed or prechecked, and refused or failed. */
        FAILED,
        /** The user declined (or closed) the approval. */
        DENIED,
        /** Not executed: skipped, deferred, blocked, or still waiting for approval. */
        NOT_EXECUTED
    }

    public record Entry(String tool, String arguments, Outcome outcome, String detail) {

        String describe() {
            return tool + " " + arguments + " -> " + outcome + (detail == null ? "" : ": " + detail);
        }
    }

    public ActionLedger {
        entries = List.copyOf(entries);
    }

    public static ActionLedger of(RunState state, ToolCatalog tools) {
        Map<String, ToolExecutionResultMessage> results = new HashMap<>();
        List<ToolExecutionRequest> actions = new ArrayList<>();
        for (ChatMessage m : state.messages()) {
            if (m instanceof ToolExecutionResultMessage r) results.put(r.id(), r);
            if (m instanceof AiMessage ai && ai.hasToolExecutionRequests()) {
                ai.toolExecutionRequests().stream()
                        .filter(r -> tools.category(r.name()).orElse(null) == ToolCategory.SENSITIVE)
                        .forEach(actions::add);
            }
        }
        List<Entry> entries = actions.stream().map(a -> entry(state, a, results.get(a.id()))).toList();
        return new ActionLedger(entries);
    }

    private static Entry entry(RunState state, ToolExecutionRequest call, ToolExecutionResultMessage result) {
        boolean stored = state.toolResults().values().stream().anyMatch(r -> r.callId().equals(call.id()));
        if (stored) return new Entry(call.name(), call.arguments(), Outcome.SUCCEEDED, null);
        HumanAnswer answer = state.humanAnswers().get(call.id());
        if (answer instanceof HumanAnswer.Dismissed
                || answer instanceof HumanAnswer.ApprovalDecision(boolean approved) && !approved) {
            return new Entry(call.name(), call.arguments(), Outcome.DENIED, "the user declined it");
        }
        if (result == null) return new Entry(call.name(), call.arguments(), Outcome.NOT_EXECUTED, null);
        Outcome outcome = Boolean.TRUE.equals(result.isError()) ? Outcome.FAILED : Outcome.NOT_EXECUTED;
        return new Entry(call.name(), call.arguments(), outcome, result.text());
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public String describe() {
        if (entries.isEmpty()) return "(no actions were attempted in this request)";
        return String.join("\n", entries.stream().map(e -> "- " + e.describe()).toList());
    }
}
