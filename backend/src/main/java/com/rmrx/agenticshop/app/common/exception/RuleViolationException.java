package com.rmrx.agenticshop.app.common.exception;

/**
 * A business rule forbids the requested change (e.g. cancelling a shipped order). The message says which rule, in
 * words the user (or an agent) can act on.
 */
public class RuleViolationException extends RuntimeException {

    public RuleViolationException(String message) {
        super(message);
    }
}
