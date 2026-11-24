package com.rmrx.agenticshop.ai.graph;

/** The turn ran past {@code app.ai.agent.turn-timeout}. */
public class TurnTimeoutException extends RuntimeException {

    public TurnTimeoutException(String message) {
        super(message);
    }
}
