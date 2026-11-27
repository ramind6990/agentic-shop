package com.rmrx.agenticshop.eval.world;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Throws for calls the world declares as failing ({@link Fault}), before the stub answers. The exception becomes the
 * tool's error result, as a real outage would.
 */
public final class Faults {

    private final List<Fault> faults;
    private final Map<String, Integer> failed = new HashMap<>();

    public Faults(List<Fault> faults) {
        this.faults = List.copyOf(faults);
    }

    /** Throws if {@code call} ({@code <Service>.<method>}) should fail now. */
    public synchronized void check(String call) {
        for (Fault fault : faults) {
            if (!call.equals(fault.call)) continue;
            int count = failed.getOrDefault(call, 0);
            if (fault.times > 0 && count >= fault.times) return;
            failed.put(call, count + 1);
            throw error(fault);
        }
    }

    private static RuntimeException error(Fault fault) {
        return switch (fault.error) {
            case "timeout" -> new IllegalStateException("The service did not answer in time (timeout).");
            case "unavailable" -> new IllegalStateException("The service is temporarily unavailable (503).");
            default -> new IllegalStateException(fault.error);
        };
    }
}
