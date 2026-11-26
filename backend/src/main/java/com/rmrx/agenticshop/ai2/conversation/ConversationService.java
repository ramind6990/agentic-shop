package com.rmrx.agenticshop.ai2.conversation;

import com.rmrx.agenticshop.ai2.activerun.ActiveRun;
import com.rmrx.agenticshop.ai2.activerun.ActiveRunRegistry;
import com.rmrx.agenticshop.ai2.contract.interaction.HumanAnswer;
import com.rmrx.agenticshop.ai2.graph.agent.pending.PendingInteraction;
import com.rmrx.agenticshop.ai2.graph.state.RunContext;
import dev.langchain4j.data.message.UserMessage;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The entry points of a conversation: a new message from the user, or the user's answer to the question or approval
 * the conversation's run is paused on. A conversation has at most one active run ({@link ActiveRunRegistry}):
 * <ul>
 * <li>while it is RUNNING, any input is rejected ({@link ConversationConflictException}) and the run continues;</li>
 * <li>while it is PAUSED, only a matching, acceptable answer resumes it; any other input abandons it — a new
 *     message then starts a new run, a non-matching answer does not.</li>
 * </ul>
 */
@Service("ai2ConversationService")
public class ConversationService {

    private final ActiveRunRegistry activeRuns;
    private final GraphRunner runner;

    public ConversationService(ActiveRunRegistry activeRuns, GraphRunner runner) {
        this.activeRuns = activeRuns;
        this.runner = runner;
    }

    /** A new message: starts a run (abandoning a paused one first). */
    public RunOutcome sendMessage(Long userId, Long conversationId, String text) {
        activeRuns.find(conversationId).ifPresent(current -> {
            checkOwner(current, userId);
            if (current.status() == ActiveRun.Status.RUNNING) {
                throw new ConversationConflictException("A reply is still being prepared in this conversation.");
            }
            activeRuns.takePaused(conversationId).ifPresent(this::abandon);
        });
        ActiveRun run = activeRuns.tryStart(conversationId, userId)
                .orElseThrow(() -> new ConversationConflictException("Another run started in this conversation."));
        RunContext context = new RunContext(userId, conversationId, run.runId());
        return execute(run, () -> runner.start(context, List.of(UserMessage.from(text))));
    }

    /**
     * The answer to the pending question or approval: resumes the run when it matches; otherwise abandons the run
     * and starts nothing.
     */
    public RunOutcome answer(Long userId, Long conversationId, UUID interactionId, HumanAnswer answer) {
        ActiveRun current = activeRuns.find(conversationId)
                .orElseThrow(() -> new ConversationConflictException("There is no question to answer."));
        checkOwner(current, userId);
        if (current.status() == ActiveRun.Status.RUNNING) {
            throw new ConversationConflictException("A reply is still being prepared in this conversation.");
        }
        PendingInteraction pending = current.pendingInteraction();
        if (pending.interactionId().equals(interactionId) && pending.accepts(answer)) {
            ActiveRun resumed = activeRuns.tryResume(conversationId, interactionId)
                    .orElseThrow(() -> new ConversationConflictException("This question was already answered."));
            return execute(resumed, () -> runner.resume(resumed.runId(), pending.callId(), answer));
        }
        return activeRuns.takePaused(conversationId)
                .map(this::abandon)
                .orElseThrow(() -> new ConversationConflictException("This question was already answered."));
    }

    /** Runs the graph for {@code run} and updates the conversation's active run with the outcome. */
    private RunOutcome execute(ActiveRun run, Supplier<RunOutcome> graphCall) {
        RunOutcome outcome;
        try {
            outcome = graphCall.get();
        } catch (RuntimeException e) {
            activeRuns.release(run.conversationId(), run.runId());
            runner.discard(run.runId());
            throw e;
        }
        switch (outcome) {
            case RunOutcome.Paused paused ->
                // TODO write the question / approval as a PENDING chat message (shown again after a reload)
                    activeRuns.markPaused(run.conversationId(), run.runId(), paused.interaction());
            case RunOutcome.Completed completed -> {
                // TODO append this run's history projection (user message, final text, compact displayed result,
                //  HITL outcomes); then summarize the history if it got too long — after the run, never at load
                activeRuns.release(run.conversationId(), run.runId());
            }
            case RunOutcome.Abandoned abandoned -> activeRuns.release(run.conversationId(), run.runId());
        }
        return outcome;
    }

    /** Drops a paused run already removed from the registry. */
    private RunOutcome.Abandoned abandon(ActiveRun paused) {
        runner.discard(paused.runId());
        // TODO mark the pending chat message NOT_ANSWERED; record in the history that the question was not answered
        //  (pending sensitive calls count as denied), so no tool call is left without a result
        return new RunOutcome.Abandoned(paused.runId(), paused.pendingInteraction());
    }

    private static void checkOwner(ActiveRun run, Long userId) {
        if (!run.userId().equals(userId)) {
            throw new AccessDeniedException("The conversation's run belongs to another user.");
        }
    }
}
