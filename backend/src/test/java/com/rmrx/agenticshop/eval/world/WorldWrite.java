package com.rmrx.agenticshop.eval.world;

import java.util.List;

/**
 * One write a tool asked the domain for, in order: {@code call} = {@code <Service>.<method>}, its arguments, and
 * {@code outcome} = {@code ok}, or {@code rejected: <reason>} when a rule refused it (nothing changed then).
 */
public record WorldWrite(String call, List<Object> args, String outcome) {

    public static final String OK = "ok";

    public static WorldWrite ok(String call, Object... args) {
        return new WorldWrite(call, java.util.Arrays.asList(args), OK);
    }

    public static WorldWrite rejected(String call, String reason, Object... args) {
        return new WorldWrite(call, java.util.Arrays.asList(args), "rejected: " + reason);
    }
}
