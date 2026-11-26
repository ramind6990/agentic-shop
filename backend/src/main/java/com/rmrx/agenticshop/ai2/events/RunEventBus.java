package com.rmrx.agenticshop.ai2.events;

import java.util.Optional;
import java.util.UUID;

/**
 * Registry of streamed runs and their buffered events, for transports. A run's events stay replayable while it runs
 * and for a retention period after its stream closes, then it is evicted.
 *
 * <p>Single node only: runs live in this JVM's memory, so a client must reach the node executing the run.
 */
public interface RunEventBus extends RunEvents {

    /** The owner of a live or retained run; empty if the run is unknown here (never opened or evicted). */
    Optional<Long> ownerOf(UUID runId);

    /**
     * Subscribes to a run: the subscriber first gets the buffered events with seq greater than afterSeq, then live
     * events, in seq order without gaps or duplicates (as long as the buffer still holds them), then onClose.
     * Empty if the run is unknown here.
     */
    Optional<RunSubscription> subscribe(UUID runId, long afterSeq, RunEventSubscriber subscriber);
}
