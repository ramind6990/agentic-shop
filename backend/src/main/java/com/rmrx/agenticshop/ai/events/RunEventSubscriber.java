package com.rmrx.agenticshop.ai.events;

import com.rmrx.agenticshop.ai.contract.event.AgentEvent;

/**
 * A transport's view of one run (SSE now, websocket later). Calls for one subscriber are never concurrent and come
 * in seq order. Throwing from either method removes the subscriber; the run and other subscribers are unaffected.
 */
public interface RunEventSubscriber {

    void onEvent(AgentEvent event);

    /** The run closed (after its last event) or this subscriber was dropped for falling too far behind. */
    void onClose();
}
