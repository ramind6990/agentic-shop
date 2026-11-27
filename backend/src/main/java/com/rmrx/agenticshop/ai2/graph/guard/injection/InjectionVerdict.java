package com.rmrx.agenticshop.ai2.graph.guard.injection;

import java.util.List;

/**
 * What the injection detection found in one text.
 *
 * @param signals names of what matched (or the classifier's reason), for the trace
 */
public record InjectionVerdict(Level level, List<String> signals) {

    public enum Level {
        /** Nothing found. */
        NONE,
        /** Weak signs only: worth a classifier's look. */
        SUSPECT,
        /** An injection attempt. */
        ATTACK
    }

    public InjectionVerdict {
        signals = List.copyOf(signals);
    }

    public static InjectionVerdict none() {
        return new InjectionVerdict(Level.NONE, List.of());
    }

    public boolean attack() {
        return level == Level.ATTACK;
    }

    public String describe() {
        return level + (signals.isEmpty() ? "" : " " + signals);
    }
}
