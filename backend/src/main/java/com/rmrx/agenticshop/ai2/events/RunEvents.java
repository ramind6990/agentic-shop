package com.rmrx.agenticshop.ai2.events;

import java.util.Optional;
import java.util.UUID;

/** The agent side of the event bus: opens a run's event stream and hands out its publisher. */
public interface RunEvents {

    /**
     * Opens the event stream of a run owned by ownerUserId and returns its publisher. Call before handing the runId to
     * the client, so a subscriber can never arrive before the stream exists. A resumed run keeps its run id: its
     * previous stream is closed and the new one continues its seq.
     */
    AgentEventPublisher open(UUID runId, Long ownerUserId);

    /** The publisher of the run's open stream; empty when the run is not streaming (closed or unknown). */
    Optional<AgentEventPublisher> publisher(UUID runId);
}
