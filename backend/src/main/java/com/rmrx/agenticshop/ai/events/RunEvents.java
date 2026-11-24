package com.rmrx.agenticshop.ai.events;

import java.util.UUID;

/** The agent side of the event bus: registers a run and hands out its publisher. */
public interface RunEvents {

    /**
     * Registers a live run owned by ownerUserId and returns its publisher. Call before handing the runId to the
     * client, so a subscriber can never arrive before the run exists. Throws if the run is already registered.
     */
    AgentEventPublisher open(UUID runId, Long ownerUserId);
}
