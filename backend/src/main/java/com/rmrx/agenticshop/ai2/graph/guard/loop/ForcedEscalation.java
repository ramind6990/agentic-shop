package com.rmrx.agenticshop.ai2.graph.guard.loop;

import com.rmrx.agenticshop.ai2.contract.interaction.HumanAnswer;
import com.rmrx.agenticshop.ai2.graph.RunLimits;
import com.rmrx.agenticshop.ai2.graph.guard.GuardTrace;
import com.rmrx.agenticshop.ai2.graph.state.RunState;
import com.rmrx.agenticshop.ai2.tools.ToolCatalog;
import com.rmrx.agenticshop.ai2.tools.ToolDefinition;
import com.rmrx.agenticshop.ai2.tools.support.TicketTools;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.request.ToolChoice;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Guardrail "code-forced escalation": once the loop detector escalated a run, code decides what the model may do next.
 * Code decides WHEN, the model writes WHAT, the customer approves SENDING:
 * <ol>
 * <li>HAND_OFF: the next model call offers only {@code escalate_to_human}, required, with a note saying why; the model
 *     writes the ticket's summary from what it knows, and the usual approval shows it to the customer;</li>
 * <li>DONE (after the hand-off's result, or right away for {@link Escalation.Mode#ANSWER_NOW}): tools off, the model
 *     gives its final reply.</li>
 * </ol>
 * An escalated run may use {@link RunLimits#ESCALATION_EXTRA_CALLS} model calls beyond the normal budget, so the
 * hand-off does not fail for lack of budget.
 */
@Component
public class ForcedEscalation {

    public enum Stage { NONE, HAND_OFF, DONE }

    private final GuardTrace trace;

    public ForcedEscalation(GuardTrace trace) {
        this.trace = trace;
    }

    /**
     * HAND_OFF until the hand-off was carried out or the customer declined it; a hand-off that failed (e.g. blocked by
     * its precheck) is tried again.
     */
    public Stage stage(RunState state) {
        Optional<Escalation> escalation = state.escalation();
        if (escalation.isEmpty()) return Stage.NONE;
        if (escalation.get().mode() == Escalation.Mode.ANSWER_NOW) return Stage.DONE;
        List<ChatMessage> messages = state.messages();
        for (int i = escalation.get().fromMessage(); i < messages.size(); i++) {
            if (messages.get(i) instanceof ToolExecutionResultMessage r
                    && TicketTools.ESCALATE_TO_HUMAN.equals(r.toolName()) && settled(state, r.id())) {
                return Stage.DONE;
            }
        }
        return Stage.HAND_OFF;
    }

    /** The tools of the next model call; {@code budgetLeft} = the normal run budget still allows tools. */
    public ModelTools modelTools(RunState state, ToolCatalog catalog, boolean budgetLeft) {
        Escalation escalation = state.escalation().orElse(null);
        return switch (stage(state)) {
            case NONE -> new ModelTools(catalog.specifications(), budgetLeft ? ToolChoice.AUTO : ToolChoice.NONE,
                    null);
            case HAND_OFF -> state.modelCallCount() + 1 >= modelCallLimit(state)
                    ? new ModelTools(catalog.specifications(), ToolChoice.NONE, FAILED_HAND_OFF_NOTE)
                    : new ModelTools(catalog.find(TicketTools.ESCALATE_TO_HUMAN).map(ToolDefinition::specification)
                    .stream().toList(), ToolChoice.REQUIRED, handOffNote(escalation));
            case DONE -> new ModelTools(catalog.specifications(), ToolChoice.NONE, doneNote(escalation));
        };
    }

    /** The hand-off call was carried out (its result is stored) or the customer declined it. */
    private static boolean settled(RunState state, String callId) {
        boolean executed = state.toolResults().values().stream().anyMatch(r -> r.callId().equals(callId));
        HumanAnswer answer = state.humanAnswers().get(callId);
        boolean declined = answer instanceof HumanAnswer.Dismissed
                || answer instanceof HumanAnswer.ApprovalDecision(boolean approved) && !approved;
        return executed || declined;
    }

    private static final String FAILED_HAND_OFF_NOTE = """
            # The hand-over could not be completed
            Give your final reply now: say that you could not pass the request to the support team this time, what \
            you found, and that the customer can ask for a person again.""";

    /** Model calls this run may make, retries included. */
    public int modelCallLimit(RunState state) {
        return stage(state) == Stage.NONE ? RunLimits.MAX_MODEL_CALLS
                : RunLimits.MAX_MODEL_CALLS + RunLimits.ESCALATION_EXTRA_CALLS;
    }

    /** While a hand-off is due: why these tool calls cannot run (any call but {@code escalate_to_human}). */
    public Optional<String> toolCallViolation(RunState state, List<ToolExecutionRequest> calls) {
        if (stage(state) != Stage.HAND_OFF) return Optional.empty();
        List<String> others = calls.stream().map(ToolExecutionRequest::name)
                .filter(name -> !TicketTools.ESCALATE_TO_HUMAN.equals(name)).toList();
        if (others.isEmpty() && calls.size() == 1) return Optional.empty();
        return Optional.of("Not executed: this request must be handed over now. Call only "
                + TicketTools.ESCALATE_TO_HUMAN + ", once.");
    }

    /** While a hand-off is due: a final reply instead of it is rejected. */
    public Optional<String> finalAnswerViolation(RunState state) {
        if (stage(state) != Stage.HAND_OFF) return Optional.empty();
        return Optional.of("this request must be handed over before you reply: call " + TicketTools.ESCALATE_TO_HUMAN);
    }

    /** Writes the escalation's start into the trace. */
    public void traceStart(RunState state, Escalation escalation) {
        trace.record(state.context().runId(), escalation.mode() == Escalation.Mode.HAND_OFF
                ? "hand-off forced" : "tools stopped", "loop detection", escalation.reason());
    }

    private static String handOffNote(Escalation escalation) {
        return """
                # Hand this request over to the support team now
                This request is not getting anywhere: %s. Do not try again. Call escalate_to_human now, with:
                - the category and a short subject;
                - the order number, only if a tool result showed that this order exists;
                - a summary for the support team: what the customer wants, what you looked up and found, and what \
                failed.
                The customer reads the summary and approves it before it is sent.""".formatted(escalation.reason());
    }

    private static String doneNote(Escalation escalation) {
        if (escalation.mode() == Escalation.Mode.ANSWER_NOW) {
            return """
                    # Stop and answer now
                    This request is not getting anywhere: %s. Tell the user what you found, what did not work, and \
                    what they could check or do by hand.""".formatted(escalation.reason());
        }
        return """
                # The hand-over is finished
                Give your final reply now: if the request was passed to the support team, say so, with the ticket \
                number and that they answer in the ticket, usually within 24 hours; if the customer declined it, say \
                that they can ask for a person at any time.""";
    }
}
