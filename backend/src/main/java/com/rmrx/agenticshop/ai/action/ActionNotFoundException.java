package com.rmrx.agenticshop.ai.action;

/**
 * The single error for any token that cannot be used: unknown, malformed, someone else's, another ticket's, used,
 * superseded, expired or of the wrong kind. The message is always the same so nothing leaks about tokens that belong
 * to others; {@link #reason()} is for server logs only and never goes to the client.
 */
public class ActionNotFoundException extends RuntimeException {

    public static final String MESSAGE = "Action not found or no longer available";

    /** Internal reason codes, logged only. */
    public enum Reason {
        MALFORMED,
        UNKNOWN,
        NO_TICKET,
        WRONG_USER,
        WRONG_TICKET,
        NOT_OPEN,
        EXPIRED,
        WRONG_KIND,
        LOST_RACE
    }

    private final Reason reason;

    public ActionNotFoundException(Reason reason) {
        super(MESSAGE, null, false, false);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
