package com.rmrx.agenticshop.ai.events;

/** Handle of one subscriber on one run. Cancelling never affects the run. */
public interface RunSubscription {

    /** Stops delivery to the subscriber; idempotent. onClose is not called. */
    void cancel();
}
