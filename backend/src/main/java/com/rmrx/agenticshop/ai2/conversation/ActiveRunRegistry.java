package com.rmrx.agenticshop.ai2.conversation;

import com.rmrx.agenticshop.ai2.graph.agent.pending.PendingInteraction;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The active run of each conversation: at most one, which makes this the conversation's lock. Every state change is
 * atomic per conversation, so concurrent requests (a second tab, a double-clicked answer) cannot both win.
 */
// TODO persist (survive restarts, work across instances); in memory for now, like the checkpoints.
@Component
public class ActiveRunRegistry {

    private final ConcurrentMap<Long, ActiveRun> runs = new ConcurrentHashMap<>();

    public Optional<ActiveRun> find(Long conversationId) {
        return Optional.ofNullable(runs.get(conversationId));
    }

    /** Starts a RUNNING run, unless the conversation already has one. */
    public Optional<ActiveRun> tryStart(Long conversationId, Long userId) {
        ActiveRun run = ActiveRun.started(conversationId, userId);
        return runs.putIfAbsent(conversationId, run) == null ? Optional.of(run) : Optional.empty();
    }

    /** The run paused on {@code interaction}; ignored when the conversation's run is no longer {@code runId}. */
    public void markPaused(Long conversationId, UUID runId, PendingInteraction interaction) {
        runs.computeIfPresent(conversationId, (id, run) -> run.runId().equals(runId) ? run.paused(interaction) : run);
    }

    /**
     * PAUSED → RUNNING, only when the run waits for {@code interactionId}. Returns the run as it was while paused
     * (with its pending interaction); empty when another request got there first.
     */
    public Optional<ActiveRun> tryResume(Long conversationId, UUID interactionId) {
        AtomicReference<ActiveRun> resumed = new AtomicReference<>();
        runs.computeIfPresent(conversationId, (id, run) -> {
            if (run.status() != ActiveRun.Status.PAUSED
                    || !run.pendingInteraction().interactionId().equals(interactionId)) {
                return run;
            }
            resumed.set(run);
            return run.running();
        });
        return Optional.ofNullable(resumed.get());
    }

    /** Removes the conversation's run if it is PAUSED (to abandon it); empty otherwise. */
    public Optional<ActiveRun> takePaused(Long conversationId) {
        AtomicReference<ActiveRun> taken = new AtomicReference<>();
        runs.computeIfPresent(conversationId, (id, run) -> {
            if (run.status() != ActiveRun.Status.PAUSED) return run;
            taken.set(run);
            return null;
        });
        return Optional.ofNullable(taken.get());
    }

    /** Ends the conversation's run if it is still {@code runId}. */
    public void release(Long conversationId, UUID runId) {
        runs.computeIfPresent(conversationId, (id, run) -> run.runId().equals(runId) ? null : run);
    }
}
