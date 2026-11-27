package com.rmrx.agenticshop.ai.model;

import com.rmrx.agenticshop.ai.trace.ErrorCodes;

/**
 * A model call that failed for good: not retryable, or still failing after every retry. {@link #errorCode()} is the
 * last attempt's code with a {@code MODEL_} prefix (e.g. {@code MODEL_HTTP_429}).
 */
public class ModelCallException extends RuntimeException implements ErrorCodes.Coded {

    private final String errorCode;
    private final int attempts;

    public ModelCallException(int attempts, Throwable cause) {
        super("model call failed after " + attempts + " attempt(s)", cause);
        this.errorCode = "MODEL_" + ErrorCodes.of(cause);
        this.attempts = attempts;
    }

    @Override
    public String errorCode() {
        return errorCode;
    }

    public int attempts() {
        return attempts;
    }
}
