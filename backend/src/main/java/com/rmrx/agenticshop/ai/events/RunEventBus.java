package com.rmrx.agenticshop.ai.events;

import java.util.Optional;
import java.util.UUID;

/**
 * Registry of live runs and their buffered events, for transports. A run's events stay replayable while it runs and
 * for a retention period after it closes, then it is evicted.
 *
 * <p>Single node only: runs live in this JVM's memory, so a client must reach the node executing the run. A
 * multi-node setup needs a shared bus (e.g. Redis streams/pub-sub or Postgres LISTEN/NOTIFY) behind this interface.
 */
public interface RunEventBus extends RunEvents {

    /** The owner of a live or retained run; empty if the run is unknown here (never registered or evicted). */
    Optional<Long> ownerOf(UUID runId);

    /**
     * Subscribes to a run: the subscriber first gets the buffered events with seq greater than afterSeq, then live
     * events, in seq order without gaps or duplicates (as long as the buffer still holds them), then onClose.
     * Empty if the run is unknown here.
     */
    Optional<RunSubscription> subscribe(UUID runId, long afterSeq, RunEventSubscriber subscriber);
}
