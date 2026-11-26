package com.rmrx.agenticshop.ai2.events;

/**
 * A transport's view of one run. Calls for one subscriber are never concurrent and come in seq order. Throwing from
 * either method removes the subscriber; the run and other subscribers are unaffected.
 */
public interface RunEventSubscriber {

    void onEvent(ChatResponse event);

    /** The stream closed (after its last event) or this subscriber was dropped for falling too far behind. */
    void onClose();
}
