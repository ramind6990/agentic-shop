package com.rmrx.agenticshop.ai2.conversation;

import com.rmrx.agenticshop.ai2.activerun.ActiveRun;
import com.rmrx.agenticshop.ai2.activerun.ActiveRunRegistry;
import com.rmrx.agenticshop.ai2.contract.reply.ReplyEnvelope;
import com.rmrx.agenticshop.ai2.contract.reply.ReplyEnvelopes;
import com.rmrx.agenticshop.ai2.conversation.message.ConversationMessageStore;
import com.rmrx.agenticshop.ai2.conversation.message.ReplyDisplay;
import com.rmrx.agenticshop.ai2.events.AgentEventPublisher;
import com.rmrx.agenticshop.ai2.events.ChatResponse;
import com.rmrx.agenticshop.ai2.events.ChatResponse.ErrorCode;
import com.rmrx.agenticshop.ai2.events.RunEvents;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.function.Supplier;

/**
 * Runs the graph of an active run in the background (one virtual thread per run) and records how it ended: the chat
 * message the user sees and the active run's new state, then the last run event (INTERACTION / REPLY). Recording comes
 * first, so the client can answer or send its next message as soon as it gets the event. A failed run is released
 * with its checkpoints and ends with an ERROR event.
 * <p>
 * A running run can be cancelled: whoever takes it out of {@code running} first — its own end, or {@link #cancel} —
 * decides how it ends, so a cancelled run never stores a late result.
 */
@Component
public class RunLauncher {

    private static final Logger log = LoggerFactory.getLogger(RunLauncher.class);

    private final ConversationMessageStore messages;
    private final ActiveRunRegistry activeRuns;
    private final GraphRunner runner;
    private final RunEvents events;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<UUID, Task> running = new ConcurrentHashMap<>();

    private record Task(ActiveRun run, AgentEventPublisher publisher, FutureTask<Void> future) {
    }

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

    /**
     * Opens the run's event stream, emits what the user sent (INPUT, with the sending client's requestId or null) and
     * starts {@code graphCall} in the background; returns at once.
     */
    public RunOutcome.Running launch(ActiveRun run, String requestId, ChatResponse.Input input,
                                     Supplier<RunOutcome> graphCall) {
        AgentEventPublisher publisher = events.open(run.runId(), run.userId(), run.conversationId());
        publisher.input(requestId, input);
        FutureTask<Void> future = new FutureTask<>(() -> execute(run, publisher, graphCall), null);
        running.put(run.runId(), new Task(run, publisher, future));
        try {
            executor.execute(future);
        } catch (RuntimeException e) {
            running.remove(run.runId());
            fail(run, publisher, ErrorCode.AGENT_ERROR);
            throw e;
        }
        return new RunOutcome.Running(run.conversationId(), run.runId());
    }

    /** Stops a running run: interrupts it, releases it with its checkpoints, ends its stream. False if not running. */
    public boolean cancel(UUID runId) {
        Task task = running.remove(runId);
        if (task == null) return false;
        task.future().cancel(true);
        log.info("ai2 run {} (conversation {}) cancelled", runId, task.run().conversationId());
        fail(task.run(), task.publisher(), ErrorCode.CANCELLED);
        return true;
    }

    private void execute(ActiveRun run, AgentEventPublisher publisher, Supplier<RunOutcome> graphCall) {
        boolean owned = false; // this run took itself out of running, so cancel can no longer end it
        try {
            RunOutcome outcome = graphCall.get();
            owned = running.remove(run.runId()) != null;
            if (!owned) return; // cancelled meanwhile
            switch (outcome) {
                case RunOutcome.Paused paused -> {
                    activeRuns.markPaused(run.conversationId(), run.runId(), paused.interaction());
                    messages.appendInteraction(run.conversationId(), run.runId(), paused.interaction());
                    publisher.pause(paused.interaction());
                }
                case RunOutcome.Completed completed -> {
                    ReplyDisplay display = completed.displayed() == null ? null
                            : ReplyDisplay.of(completed.displayed(), completed.response().finalReferenceToDisplay());
                    ReplyEnvelope reply = ReplyEnvelopes.of(completed.response(), completed.displayed());
                    messages.appendReply(run.conversationId(), run.runId(), completed.response(), display, reply);
                    activeRuns.release(run.conversationId(), run.runId());
                    publisher.complete(reply);
                }
                case RunOutcome other -> throw new IllegalStateException("unexpected graph outcome: " + other);
            }
        } catch (Throwable e) {
            if (!owned && running.remove(run.runId()) == null) {
                // cancel took it (its interrupt is usually what failed here) and ends it
                log.debug("ai2 run {} ended after being cancelled: {}", run.runId(), e.toString());
                return;
            }
            log.error("ai2 run {} (conversation {}) failed", run.runId(), run.conversationId(), e);
            fail(run, publisher, ErrorCode.AGENT_ERROR);
        }
    }

    // TODO store a visible error reply so the user's message does not stay unanswered in the timeline
    private void fail(ActiveRun run, AgentEventPublisher publisher, ErrorCode code) {
        try {
            activeRuns.release(run.conversationId(), run.runId());
            runner.discard(run.runId());
        } catch (RuntimeException e) {
            log.error("could not release ai2 run {}", run.runId(), e);
        }
        if (code == ErrorCode.CANCELLED) publisher.fail(code, "Stopped.", false);
        else publisher.fail(code, "The assistant could not complete this reply.", true);
    }
}
