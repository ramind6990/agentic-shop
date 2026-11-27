package com.rmrx.agenticshop.ai2.contract.reply;

import com.rmrx.agenticshop.ai2.graph.agent.response.ResponseStatus;

/**
 * How a reply came about. ANSWERED / REFUSED come from the agent's response; FAILED and STOPPED are written by the
 * system when a run ends without one (kept out of the model's own {@link ResponseStatus}).
 */
public enum ReplyStatus {
    ANSWERED,
    REFUSED,
    /** The run failed; the text says so and the user can try again. */
    FAILED,
    /** The user stopped the run before it replied. */
    STOPPED;

    public static ReplyStatus of(ResponseStatus status) {
        return status == ResponseStatus.REFUSED ? REFUSED : ANSWERED;
    }
}
