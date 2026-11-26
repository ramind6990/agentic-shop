package com.rmrx.agenticshop.ai2.conversation;

import com.rmrx.agenticshop.ai2.activerun.ActiveRun;
import com.rmrx.agenticshop.ai2.activerun.ActiveRunRegistry;
import com.rmrx.agenticshop.ai2.conversation.message.ConversationMessageStore;
import com.rmrx.agenticshop.ai2.conversation.message.ReplyDisplay;
import com.rmrx.agenticshop.ai2.events.AgentEventPublisher;
import com.rmrx.agenticshop.ai2.events.RunEvents;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Runs the graph of an active run in the background (one virtual thread per run) and records how it ended: the chat
 * message the user sees and the active run's new state, then the last run event (PAUSED / FINAL). Recording comes
 * first, so the client can answer or send its next message as soon as it gets the event. A failed run is released
 * with its checkpoints and ends with an ERROR event.
 */
@Component
public class RunLauncher {

    private static final Logger log = LoggerFactory.getLogger(RunLauncher.class);

    private final ConversationMessageStore messages;
    private final ActiveRunRegistry activeRuns;
    private final GraphRunner runner;
    private final RunEvents events;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public RunLauncher(ConversationMessageStore messages, ActiveRunRegistry activeRuns, GraphRunner runner,
                       RunEvents events) {
        this.messages = messages;
        this.activeRuns = activeRuns;
        this.runner = runner;
        this.events = events;
    }

    @PreDestroy
    void shutdown() {
        // runs cut off here stay RUNNING and are dropped at the next startup
        executor.shutdownNow();
    }

    /** Opens the run's event stream and starts {@code graphCall} in the background; returns at once. */
    public RunOutcome.Running launch(ActiveRun run, Supplier<RunOutcome> graphCall) {
        AgentEventPublisher publisher = events.open(run.runId(), run.userId());
        publisher.runStarted(run.conversationId());
        try {
            executor.execute(() -> execute(run, publisher, graphCall));
        } catch (RuntimeException e) {
            fail(run, publisher);
            throw e;
        }
        return new RunOutcome.Running(run.conversationId(), run.runId());
    }

    private void execute(ActiveRun run, AgentEventPublisher publisher, Supplier<RunOutcome> graphCall) {
        try {
            switch (graphCall.get()) {
                case RunOutcome.Paused paused -> {
                    activeRuns.markPaused(run.conversationId(), run.runId(), paused.interaction());
                    messages.appendInteraction(run.conversationId(), run.runId(), paused.interaction());
                    publisher.pause(paused.interaction());
                }
                case RunOutcome.Completed completed -> {
                    ReplyDisplay display = completed.displayed() == null ? null
                            : ReplyDisplay.of(completed.displayed(), completed.response().finalReferenceToDisplay());
                    messages.appendReply(run.conversationId(), run.runId(), completed.response(), display);
                    activeRuns.release(run.conversationId(), run.runId());
                    publisher.complete(completed.response(), completed.displayed());
                }
                case RunOutcome other -> throw new IllegalStateException("unexpected graph outcome: " + other);
            }
        } catch (Throwable e) {
            log.error("ai2 run {} (conversation {}) failed", run.runId(), run.conversationId(), e);
            fail(run, publisher);
        }
    }

    // TODO store a visible error reply so the user's message does not stay unanswered in the timeline
    private void fail(ActiveRun run, AgentEventPublisher publisher) {
        try {
            activeRuns.release(run.conversationId(), run.runId());
            runner.discard(run.runId());
        } catch (RuntimeException e) {
            log.error("could not release failed ai2 run {}", run.runId(), e);
        }
        publisher.fail("AGENT_ERROR", "The assistant could not complete this reply.", true);
    }
}
