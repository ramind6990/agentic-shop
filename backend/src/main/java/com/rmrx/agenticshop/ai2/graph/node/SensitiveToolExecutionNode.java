package com.rmrx.agenticshop.ai2.graph.node;

import com.rmrx.agenticshop.ai2.contract.interaction.HumanAnswer;
import com.rmrx.agenticshop.ai2.contract.tool.ToolResult;
import com.rmrx.agenticshop.ai2.graph.agent.pending.PendingInteraction;
import com.rmrx.agenticshop.ai2.graph.state.RunState;
import com.rmrx.agenticshop.ai2.tools.ExecutedCall;
import com.rmrx.agenticshop.ai2.tools.ToolCallExecutor;
import com.rmrx.agenticshop.ai2.tools.ToolRegistry;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.InterruptibleAction;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.action.NodeActionWithConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Pauses the run once per unapproved sensitive tool call to get the user's confirmation, then executes the approved
 * calls and writes a denied result for the rest.
 * <p>
 * langgraph4j calls {@link #interrupt} every time the node is entered, including after a resume, so it is idempotent:
 * it pauses on the first sensitive call that has no decision in {@link RunState#humanAnswers()} yet. {@link #apply}
 * runs once every call is decided.
 */
public class SensitiveToolExecutionNode implements NodeActionWithConfig<RunState>, InterruptibleAction<RunState> {

    public static final String REASON = "APPROVAL";

    private final ToolRegistry registry;
    private final ToolCallExecutor executor;

    public SensitiveToolExecutionNode(ToolRegistry registry, ToolCallExecutor executor) {
        this.registry = registry;
        this.executor = executor;
    }

    @Override
    public Optional<InterruptionMetadata<RunState>> interrupt(String nodeId, RunState state, RunnableConfig config) {
        return nextPendingApproval(state).map(call -> InterruptionMetadata.builder(nodeId, state)
                .reason(REASON)
                .putMetadata(PendingInteraction.METADATA_KEY, toApprovalRequest(state.context().runId(), call))
                .build());
    }

    /**
     * Executes approved calls (each successful result stored under a new ref); a denied result for the others
     * (denied or dismissed). One result message per call.
     */
    @Override
    public Map<String, Object> apply(RunState state, RunnableConfig config) {
        Map<String, HumanAnswer> answers = state.humanAnswers();
        List<ChatMessage> messages = new ArrayList<>();
        Map<String, ToolResult> results = new LinkedHashMap<>();
        for (ToolExecutionRequest call : state.lastToolRequests(state.stepPlan().sensitiveCallIds())) {
            if (answers.get(call.id()) instanceof HumanAnswer.ApprovalDecision(boolean approved) && approved) {
                ExecutedCall executed = executor.execute(call, state.context(), state.nextResultRef(results.size()));
                messages.add(executed.message());
                if (executed.result() != null) results.put(executed.result().ref(), executed.result());
            } else {
                messages.add(ToolExecutionResultMessage.from(call, HumanAnswer.ApprovalDecision.DENIED_RESULT));
            }
        }
        return Map.of(RunState.MESSAGES, messages, RunState.TOOL_RESULTS, results);
    }

    /** The first sensitive call of this step, in the model's order, with no decision yet. */
    private Optional<ToolExecutionRequest> nextPendingApproval(RunState state) {
        Map<String, HumanAnswer> answers = state.humanAnswers();
        return state.lastToolRequests(state.stepPlan().sensitiveCallIds()).stream()
                .filter(call -> !answers.containsKey(call.id()))
                .findFirst();
    }

    /** Title and detail come from the tool's {@code @Sensitive} templates, rendered from the call's arguments. */
    private PendingInteraction.ApprovalRequest toApprovalRequest(UUID runId, ToolExecutionRequest call) {
        String title = registry.approvalMessage(call)
                .orElseThrow(() -> new IllegalStateException("not a sensitive tool: " + call.name()));
        return new PendingInteraction.ApprovalRequest(PendingInteraction.idFor(runId, call.id()), call.id(),
                title, registry.approvalDetail(call).orElse(null));
    }
}
