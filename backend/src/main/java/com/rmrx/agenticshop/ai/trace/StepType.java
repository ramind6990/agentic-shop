package com.rmrx.agenticshop.ai.trace;

/** What a trace step records ({@code ai_run_step.step_type}). */
public enum StepType {
    /** What the user sent: the message that started the run, or the answer that resumed it. */
    INPUT,
    /** One model attempt (a retry is another attempt). */
    MODEL,
    /** One executed tool call. */
    TOOL,
    /** A model output the validation rejected, or tool calls it dropped. */
    VALIDATION,
    /** A guardrail decision: blocked input, hidden tool text, a precheck refusal, a reply audit, a forced hand-off. */
    GUARD,
    /** The question or approval the run paused on. */
    INTERACTION,
    /** The reply the user got. */
    REPLY,
    /** The error that ended the run. */
    ERROR
}
