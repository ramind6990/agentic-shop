package com.rmrx.agenticshop.ai.conversation;

import com.rmrx.agenticshop.ai.activerun.ActiveRun;
import com.rmrx.agenticshop.ai.contract.reply.ReplyEnvelope;
import com.rmrx.agenticshop.ai.contract.reply.ReplyEnvelopes;
import com.rmrx.agenticshop.ai.events.AgentEventPublisher;
import com.rmrx.agenticshop.ai.events.ChatResponse;
import com.rmrx.agenticshop.ai.events.ChatResponse.ErrorCode;
import com.rmrx.agenticshop.ai.events.RunEvents;
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
 * Runs the graph of an active run in the background (one virtual thread per run). How it ends is recorded first
 * ({@link RunRecorder}: chat message, active run row, trace), then the last run event (INTERACTION / REPLY / ERROR) is
 * emitted, so the client can answer or send its next message as soon as it gets the event. A failed run gets a
 * failure reply and ends with an ERROR event.
 * <p>
 * A running run can be cancelled: whoever takes it out of {@code running} first — its own end, or {@link #cancel} —
 * decides how it ends, so a cancelled run never stores a late result.
 */
@Component
public class RunLauncher {

    private static final Logger log = LoggerFactory.getLogger(RunLauncher.class);

    private final RunRecorder recorder;
    private final RunEvents events;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<UUID, Task> running = new ConcurrentHashMap<>();

    private record Task(ActiveRun run, AgentEventPublisher publisher, FutureTask<Void> future) {
    }

    public RunLauncher(RunRecorder recorder, RunEvents events) {
        this.recorder = recorder;
        this.events = events;
    }

    @PreDestroy
    void shutdown() {
        // runs cut off here stay RUNNING and get a failure reply at the next startup
        executor.shutdownNow();
    }

    /**
     * Opens the run's event stream, emits what the user sent (INPUT, with the sending client's requestId or null),
     * records the start and starts {@code graphCall} in the background; returns at once.
     */
    public RunOutcome.Running launch(ActiveRun run, String requestId, ChatResponse.Input input,
                                     Supplier<RunOutcome> graphCall) {
        AgentEventPublisher publisher = events.open(run.runId(), run.userId(), run.conversationId());
        publisher.input(requestId, input);
        recorder.started(run, input);
        FutureTask<Void> future = new FutureTask<>(() -> execute(run, publisher, graphCall), null);
        running.put(run.runId(), new Task(run, publisher, future));
        try {
            executor.execute(future);
        } catch (RuntimeException e) {
            running.remove(run.runId());
            fail(run, publisher, e);
            throw e;
        }
        return new RunOutcome.Running(run.conversationId(), run.runId());
    }

    /** Stops a running run: interrupts it, records it as stopped, ends its stream. False if not running. */
    public boolean cancel(UUID runId) {
        Task task = running.remove(runId);
        if (task == null) return false;
        task.future().cancel(true);
        log.info("ai run {} (conversation {}) cancelled", runId, task.run().conversationId());
        recorder.cancelled(task.run());
        task.publisher().fail(ErrorCode.CANCELLED, ReplyEnvelopes.STOPPED_TEXT, false);
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
                    recorder.paused(run, paused.interaction());
                    publisher.pause(paused.interaction());
                }
                case RunOutcome.Completed completed -> publisher.complete(recorder.completed(run, completed));
                case RunOutcome other -> throw new IllegalStateException("unexpected graph outcome: " + other);
            }
        } catch (Throwable e) {
            if (!owned && running.remove(run.runId()) == null) {
                // cancel took it (its interrupt is usually what failed here) and ends it
                log.debug("ai run {} ended after being cancelled: {}", run.runId(), e.toString());
                return;
            }
            log.error("ai run {} (conversation {}) failed", run.runId(), run.conversationId(), e);
            fail(run, publisher, e);
        }
    }

    private void fail(ActiveRun run, AgentEventPublisher publisher, Throwable error) {
        ReplyEnvelope reply = recorder.failed(run, error);
        publisher.fail(ErrorCode.AGENT_ERROR, reply.text(), true);
    }
}
