package com.rmrx.agenticshop.ai2.graph.guard.loop;

import com.rmrx.agenticshop.ai2.contract.interaction.InteractionJson;
import com.rmrx.agenticshop.ai2.graph.state.RunState;
import com.rmrx.agenticshop.ai2.tools.ToolCatalog;
import com.rmrx.agenticshop.ai2.tools.ToolCategory;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the run's earlier steps did with tools (the step being validated excluded): failures per tool, distinct
 * argument sets per tool, tools the loop detector already blocked, and how many of the latest steps made no progress.
 */
final class RunTrajectory {

    final Map<String, Integer> failuresByTool = new HashMap<>();
    final Map<String, Set<Object>> argumentsByTool = new HashMap<>();
    final Set<String> loopBlockedTools = new HashSet<>();
    int toolErrors;
    int trailingIdleSteps;

    static RunTrajectory of(RunState state, ToolCatalog tools) {
        RunTrajectory t = new RunTrajectory();
        List<ChatMessage> messages = state.messages();
        Map<String, ToolExecutionResultMessage> results = new HashMap<>();
        messages.forEach(m -> {
            if (m instanceof ToolExecutionResultMessage r) results.put(r.id(), r);
        });
        int current = lastAiIndex(messages);
        for (int i = 0; i < current; i++) {
            if (messages.get(i) instanceof AiMessage ai && ai.hasToolExecutionRequests()) {
                boolean progress = false;
                for (ToolExecutionRequest call : ai.toolExecutionRequests()) {
                    progress |= t.record(state, tools, call, results.get(call.id()));
                }
                t.trailingIdleSteps = progress ? 0 : t.trailingIdleSteps + 1;
            }
        }
        return t;
    }

    /** Records one earlier call; true when it moved the run forward. */
    private boolean record(RunState state, ToolCatalog tools, ToolExecutionRequest call,
                           ToolExecutionResultMessage result) {
        ToolCategory category = tools.category(call.name()).orElse(null);
        argumentsByTool.computeIfAbsent(call.name(), k -> new HashSet<>()).add(argumentsKey(call));
        if (result != null && Boolean.TRUE.equals(result.isError())) {
            failuresByTool.merge(call.name(), 1, Integer::sum);
            toolErrors++;
        }
        if (result != null && result.text() != null && result.text().startsWith(LoopDetector.BLOCKED_PREFIX)) {
            loopBlockedTools.add(call.name());
        }
        boolean stored = state.toolResults().values().stream().anyMatch(r -> r.callId().equals(call.id()));
        boolean answered = state.humanAnswers().containsKey(call.id());
        return stored || answered || category == ToolCategory.ASK;
    }

    /** A call's arguments as a JSON tree (key order ignored), or the raw text when it does not parse. */
    static Object argumentsKey(ToolExecutionRequest call) {
        String arguments = call.arguments();
        if (arguments == null || arguments.isBlank()) return "{}";
        try {
            return InteractionJson.tree(arguments);
        } catch (RuntimeException e) {
            return arguments;
        }
    }

    private static int lastAiIndex(List<ChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof AiMessage) return i;
        }
        return messages.size();
    }
}
