package com.rmrx.agenticshop.ai.trace;

import com.rmrx.agenticshop.ai.usage.TokenCounts;

/**
 * One step to write into a run's trace. Summaries are cut by the recorder.
 *
 * @param attempt MODEL only: 1 for the first try, 2.. for retries
 * @param tokens  MODEL only, when the call succeeded
 */
public record TraceStep(StepType type, String name, Integer attempt, String args, String result, String errorCode,
                        Long latencyMs, TokenCounts tokens) {

    public static TraceStep model(String model, int attempt, String request, String response, String errorCode,
                                  long latencyMs, TokenCounts tokens) {
        return new TraceStep(StepType.MODEL, model, attempt, request, response, errorCode, latencyMs, tokens);
    }

    public static TraceStep tool(String tool, String arguments, String result, String errorCode, long latencyMs) {
        return new TraceStep(StepType.TOOL, tool, null, arguments, result, errorCode, latencyMs, null);
    }

    public static TraceStep of(StepType type, String name, String args, String result) {
        return new TraceStep(type, name, null, args, result, null, null, null);
    }

    public static TraceStep error(String errorCode, String message) {
        return new TraceStep(StepType.ERROR, "run failed", null, null, message, errorCode, null, null);
    }
}
