package com.rmrx.agenticshop.ai.contract.response;

/** Lifecycle of an {@link ItemAction}: only OPEN actions can still be used. */
public enum ActionState {
    OPEN,
    USED,
    EXPIRED,
    SUPERSEDED
}
