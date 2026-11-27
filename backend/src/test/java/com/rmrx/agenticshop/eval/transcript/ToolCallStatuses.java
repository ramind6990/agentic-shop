package com.rmrx.agenticshop.eval.transcript;

import com.rmrx.agenticshop.ai.contract.interaction.HumanAnswer;
import com.rmrx.agenticshop.ai.events.ChatResponse;
import com.rmrx.agenticshop.eval.harness.ModelCallRecord;
import com.rmrx.agenticshop.eval.harness.ToolCallView;
import com.rmrx.agenticshop.eval.harness.TurnLog;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The model's tool calls of one turn with what became of each: the result text it was given (from later model
 * requests) and a status. Executed calls are matched, per tool name in order, to the run's TOOL_FINISHED events
 * (an approved call finishes in the segment after its pause); calls answered with a "not executed" text never ran.
 */
final class ToolCallStatuses {

    private static final Set<String> ASK_TOOLS = Set.of("ask_choice", "ask_input", "ask_yes_no");
    private static final String PRECHECK_PREFIX = "Not executed (checked before asking for approval)";

    private ToolCallStatuses() {
    }

    /** Per segment, its tool calls. {@code results}: call id → result text, over the whole scenario. */
    static List<List<Transcript.ToolCallRecord>> of(List<TurnLog.Segment> segments, Map<String, String> results) {
        Map<String, Deque<Boolean>> finished = finishedByTool(segments);
        List<List<Transcript.ToolCallRecord>> out = new ArrayList<>();
        for (TurnLog.Segment segment : segments) {
            List<Transcript.ToolCallRecord> calls = new ArrayList<>();
            segment.modelCalls().stream()
                    .filter(c -> c.purpose() == ModelCallRecord.Purpose.AGENT)
                    .flatMap(c -> c.toolCalls().stream())
                    .forEach(call -> {
                        String result = results.get(call.id());
                        calls.add(new Transcript.ToolCallRecord(call.id(), call.name(),
                                ToolCallView.parse(call.arguments()), status(call.name(), result, finished), result));
                    });
            out.add(calls);
        }
        return out;
    }

    private static String status(String tool, String result, Map<String, Deque<Boolean>> finished) {
        if (ASK_TOOLS.contains(tool)) return "asked";
        if (result != null) {
            if (result.startsWith(HumanAnswer.ApprovalDecision.DENIED_RESULT)) return "denied";
            if (result.startsWith(PRECHECK_PREFIX)) return "blocked";
            if (result.startsWith("Not executed") || result.startsWith("Skipped")) return "not_executed";
        }
        Boolean success = finished.getOrDefault(tool, new ArrayDeque<>()).poll();
        if (success == null) return "unknown";
        return success ? "executed" : "failed";
    }

    private static Map<String, Deque<Boolean>> finishedByTool(List<TurnLog.Segment> segments) {
        Map<String, Deque<Boolean>> finished = new HashMap<>();
        segments.stream()
                .flatMap(s -> s.events().stream())
                .filter(e -> e.type() == ChatResponse.Type.PROGRESS && e.progress() != null
                        && e.progress().stage() == ChatResponse.Stage.TOOL_FINISHED)
                .forEach(e -> finished.computeIfAbsent(e.progress().toolName(), k -> new ArrayDeque<>())
                        .add(Boolean.TRUE.equals(e.progress().success())));
        return finished;
    }
}
