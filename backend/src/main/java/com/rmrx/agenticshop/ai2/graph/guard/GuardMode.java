package com.rmrx.agenticshop.ai2.graph.guard;

/** How a guardrail acts on what it finds. */
public enum GuardMode {

    /** The check does not run. */
    OFF,

    /** The check runs and writes its findings to the run's trace, but changes nothing (to measure false positives). */
    SHADOW,

    /** The check runs and acts: blocks, hides or rejects. */
    ENFORCE;

    public boolean runs() {
        return this != OFF;
    }

    public boolean enforced() {
        return this == ENFORCE;
    }
}
