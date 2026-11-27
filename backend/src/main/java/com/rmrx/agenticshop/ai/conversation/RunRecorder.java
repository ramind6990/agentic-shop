package com.rmrx.agenticshop.ai.conversation;

import com.rmrx.agenticshop.ai.activerun.ActiveRun;
import com.rmrx.agenticshop.ai.activerun.ActiveRunRegistry;
import com.rmrx.agenticshop.ai.contract.interaction.InteractionJson;
import com.rmrx.agenticshop.ai.contract.reply.ReplyEnvelope;
import com.rmrx.agenticshop.ai.contract.reply.ReplyEnvelopes;
import com.rmrx.agenticshop.ai.conversation.message.ConversationMessageStore;
import com.rmrx.agenticshop.ai.conversation.message.InteractionStatus;
import com.rmrx.agenticshop.ai.conversation.message.ReplyDisplay;
import com.rmrx.agenticshop.ai.events.ChatResponse;
import com.rmrx.agenticshop.ai.graph.agent.pending.PendingInteraction;
import com.rmrx.agenticshop.ai.model.ModelCallException;
import com.rmrx.agenticshop.ai.trace.ErrorCodes;
import com.rmrx.agenticshop.ai.trace.RunStatus;
import com.rmrx.agenticshop.ai.trace.StepType;
import com.rmrx.agenticshop.ai.trace.TraceRecorder;
import com.rmrx.agenticshop.ai.trace.TraceStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Records a run's start and how it ended: the chat message the user sees, the conversation's active run row, the
 * graph checkpoints and the run's trace. {@link RunLauncher} calls it before emitting the matching run event, so a
 * client that gets the event finds the timeline already up to date.
 * <p>
 * A run that ends without a reply (failed, stopped, cut off by a restart) still gets an ASSISTANT message (a FAILED or
 * STOPPED envelope), so the user's message never stays unanswered and the next run's model knows what happened.
 */
@Component
public class RunRecorder {

    private static final Logger log = LoggerFactory.getLogger(RunRecorder.class);

    private final ConversationMessageStore messages;
    private final ActiveRunRegistry activeRuns;
    private final GraphRunner runner;
    private final TraceRecorder trace;

    public RunRecorder(ConversationMessageStore messages, ActiveRunRegistry activeRuns, GraphRunner runner,
                       TraceRecorder trace) {
        this.messages = messages;
        this.activeRuns = activeRuns;
        this.runner = runner;
        this.trace = trace;
    }

    /** The run starts, or resumes after a pause, with what the user sent. */
    public void started(ActiveRun run, ChatResponse.Input input) {
        trace.runStarted(run.runId(), run.conversationId(), run.userId());
        trace.step(run.runId(), input.answered() == null
                ? TraceStep.of(StepType.INPUT, "message", null, input.text())
                : TraceStep.of(StepType.INPUT, "answer", input.answered().promptText(),
                InteractionJson.write(input.answer())));
    }

    /** The run waits for the user's answer: the question goes into the timeline and onto the active run. */
    public void paused(ActiveRun run, PendingInteraction interaction) {
        activeRuns.markPaused(run.conversationId(), run.runId(), interaction);
        messages.appendInteraction(run.conversationId(), run.runId(), interaction);
        trace.step(run.runId(), TraceStep.of(StepType.INTERACTION, interaction.getClass().getSimpleName(),
                interaction.callId(), InteractionJson.write(interaction)));
        trace.runPaused(run.runId());
    }

    /** The run replied: stores the reply and releases the run. Returns the envelope to emit. */
    public ReplyEnvelope completed(ActiveRun run, RunOutcome.Completed completed) {
        ReplyDisplay display = completed.displayed() == null ? null
                : ReplyDisplay.of(completed.displayed(), completed.response().finalReferenceToDisplay());
        ReplyEnvelope reply = ReplyEnvelopes.of(completed.response(), completed.displayed());
        messages.appendReply(run.conversationId(), run.runId(), completed.response(), display, reply);
        activeRuns.release(run.conversationId(), run.runId());
        trace.step(run.runId(), TraceStep.of(StepType.REPLY, reply.status().name(),
                reply.block() == null ? "no block" : "block " + reply.block().getClass().getSimpleName()
                        + " from " + completed.displayed().ref(), reply.text()));
        trace.runFinished(run.runId(), RunStatus.COMPLETED, null, null);
        return reply;
    }

    /** The run failed: releases it and stores a failure reply. Returns the envelope (its text goes to the client). */
    public ReplyEnvelope failed(ActiveRun run, Throwable error) {
        release(run);
        boolean modelDown = ErrorCodes.find(error, ModelCallException.class).isPresent();
        ReplyEnvelope reply = ReplyEnvelopes.failed(
                modelDown ? ReplyEnvelopes.UNAVAILABLE_TEXT : ReplyEnvelopes.FAILED_TEXT);
        appendOutcome(run.conversationId(), run.runId(), reply);
        String code = ErrorCodes.of(error);
        String message = ErrorCodes.message(error);
        trace.step(run.runId(), TraceStep.error(code, message));
        trace.runFinished(run.runId(), RunStatus.FAILED, code, message);
        return reply;
    }

    /** The user stopped the run: releases it and stores the "Stopped." marker. */
    public void cancelled(ActiveRun run) {
        release(run);
        appendOutcome(run.conversationId(), run.runId(), ReplyEnvelopes.stopped());
        trace.step(run.runId(), TraceStep.of(StepType.REPLY, "STOPPED", null, "stopped by the user"));
        trace.runFinished(run.runId(), RunStatus.CANCELLED, null, null);
    }

    /**
     * Drops a paused run already removed from the registry (the user sent something that did not answer it); its
     * question stays in the timeline as NOT_ANSWERED.
     */
    public RunOutcome.Abandoned abandoned(ActiveRun paused) {
        runner.discard(paused.runId());
        messages.resolveInteraction(paused.pendingInteraction().interactionId(), InteractionStatus.NOT_ANSWERED, null);
        trace.runFinished(paused.runId(), RunStatus.ABANDONED, null, "the next input did not answer the question");
        return new RunOutcome.Abandoned(paused.conversationId(), paused.runId(), paused.pendingInteraction());
    }

    /**
     * Runs still RUNNING at startup were cut off by the previous shutdown and will never finish: unlock their
     * conversations and tell the user their message was not answered.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterrupted() {
        for (ActiveRun run : activeRuns.dropInterrupted()) {
            appendOutcome(run.conversationId(), run.runId(), ReplyEnvelopes.failed(ReplyEnvelopes.INTERRUPTED_TEXT));
            trace.step(run.runId(), TraceStep.error("INTERRUPTED", "cut off by a restart"));
            trace.runFinished(run.runId(), RunStatus.FAILED, "INTERRUPTED", "cut off by a restart");
        }
    }

    private void release(ActiveRun run) {
        try {
            activeRuns.release(run.conversationId(), run.runId());
            runner.discard(run.runId());
        } catch (RuntimeException e) {
            log.error("could not release ai run {}", run.runId(), e);
        }
    }

    private void appendOutcome(Long conversationId, UUID runId, ReplyEnvelope reply) {
        try {
            messages.appendOutcome(conversationId, runId, reply);
        } catch (RuntimeException e) {
            log.error("could not store the {} reply of ai run {}", reply.status(), runId, e);
        }
    }
}
