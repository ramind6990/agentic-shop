package com.rmrx.agenticshop.ai.conversation;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * At most one active agent run per ticket, in memory. A lease is taken before the user's message is saved (so a
 * rejected send leaves nothing behind) and released when the turn ends, whatever the outcome. Single-instance
 * only: with several app instances this becomes a DB or Redis lock.
 */
@Component
public class ActiveRuns {

    /** A held slot; runId is set once the run exists. */
    public static final class Lease {
        private final Long ticketId;
        private volatile UUID runId;

        private Lease(Long ticketId) {
            this.ticketId = ticketId;
        }

        public Long ticketId() {
            return ticketId;
        }

        public UUID runId() {
            return runId;
        }

        void bind(UUID runId) {
            this.runId = runId;
        }
    }

    private final Map<Long, Lease> active = new ConcurrentHashMap<>();

    /** A new lease, or empty if the ticket already has an active run. */
    public Optional<Lease> tryAcquire(Long ticketId) {
        Lease lease = new Lease(ticketId);
        return active.putIfAbsent(ticketId, lease) == null ? Optional.of(lease) : Optional.empty();
    }

    public void bind(Lease lease, UUID runId) {
        lease.bind(runId);
    }

    public void release(Lease lease) {
        active.remove(lease.ticketId(), lease);
    }

    public boolean isActive(Long ticketId) {
        return active.containsKey(ticketId);
    }

    /** The active run of a ticket, if one is running and already has its id. */
    public Optional<UUID> activeRun(Long ticketId) {
        return Optional.ofNullable(active.get(ticketId)).map(Lease::runId);
    }
}
