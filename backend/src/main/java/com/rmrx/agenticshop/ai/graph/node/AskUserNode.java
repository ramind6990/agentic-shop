package com.rmrx.agenticshop.ai.graph.node;

import com.rmrx.agenticshop.ai.tools.ask.AskChoiceArgs;
import com.rmrx.agenticshop.ai.tools.ask.AskInputArgs;
import com.rmrx.agenticshop.ai.tools.ask.AskUserTools;
import com.rmrx.agenticshop.ai.tools.ask.AskYesNoArgs;
import com.rmrx.agenticshop.ai.contract.interaction.HumanAnswer;
import com.rmrx.agenticshop.ai.contract.interaction.InteractionJson;
import com.rmrx.agenticshop.ai.graph.agent.pending.PendingInteraction;
import com.rmrx.agenticshop.ai.graph.guard.injection.UserInputGuard;
import com.rmrx.agenticshop.ai.graph.state.RunState;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.InterruptibleAction;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.action.NodeActionWithConfig;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Pauses the run once per unanswered ask call ({@code ask_choice}, {@code ask_input}, {@code ask_yes_no}), then
 * writes each answer as the tool result of its call.
 * <p>
 * langgraph4j calls {@link #interrupt} every time the node is entered, including after a resume, so it is idempotent:
 * it pauses on the first ask call that has no answer in {@link RunState#humanAnswers()} yet. {@link #apply} runs once
 * every call is answered.
 */
public class AskUserNode implements NodeActionWithConfig<RunState>, InterruptibleAction<RunState> {

    public static final String REASON = "ASK_USER";

    private final UserInputGuard inputGuard;

    public AskUserNode(UserInputGuard inputGuard) {
        this.inputGuard = inputGuard;
    }

    @Override
    public Optional<InterruptionMetadata<RunState>> interrupt(String nodeId, RunState state, RunnableConfig config) {
        return nextPendingQuestion(state).map(call -> InterruptionMetadata.builder(nodeId, state)
                .reason(REASON)
                .putMetadata(PendingInteraction.METADATA_KEY, toInteraction(state.context().runId(), call))
                .build());
    }

    @Override
    public Map<String, Object> apply(RunState state, RunnableConfig config) {
        return Map.of(RunState.MESSAGES, answersAsToolResults(state));
    }

    /** The first ask call of this step, in the model's order, that has no answer yet. */
    private Optional<ToolExecutionRequest> nextPendingQuestion(RunState state) {
        Map<String, HumanAnswer> answers = state.humanAnswers();
        return state.lastToolRequests(state.stepPlan().askCallIds()).stream()
                .filter(call -> !answers.containsKey(call.id()))
                .findFirst();
    }

    /** The question the UI shows, from the model's arguments (already checked by the validation node). */
    private PendingInteraction toInteraction(UUID runId, ToolExecutionRequest call) {
        UUID id = PendingInteraction.idFor(runId, call.id());
        return switch (call.name()) {
            case AskUserTools.ASK_CHOICE -> {
                AskChoiceArgs args = InteractionJson.read(call.arguments(), AskChoiceArgs.class);
                yield new PendingInteraction.ChoiceQuestion(id, call.id(), args.question(), args.options(),
                        args.multiSelect());
            }
            case AskUserTools.ASK_INPUT -> new PendingInteraction.InputQuestion(id, call.id(),
                    InteractionJson.read(call.arguments(), AskInputArgs.class).question());
            case AskUserTools.ASK_YES_NO -> new PendingInteraction.YesNoQuestion(id, call.id(),
                    InteractionJson.read(call.arguments(), AskYesNoArgs.class).question());
            default -> throw new IllegalStateException("not an ask tool: " + call.name());
        };
    }

    /**
     * One tool result per ask call of this step, holding the user's answer as the input guardrail lets it through (a
     * typed answer that looks like a prompt injection is withheld).
     */
    private List<ChatMessage> answersAsToolResults(RunState state) {
        Map<String, HumanAnswer> answers = state.humanAnswers();
        return state.lastToolRequests(state.stepPlan().askCallIds()).stream()
                .<ChatMessage>map(call -> ToolExecutionResultMessage.from(call,
                        inputGuard.answerToolResult(state.context(), answers.get(call.id()))))
                .toList();
    }
}
