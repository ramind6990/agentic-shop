package com.rmrx.agenticshop.eval.transcript;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rmrx.agenticshop.eval.world.EvalWorld;
import com.rmrx.agenticshop.eval.world.WorldWrite;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything one scenario run produced, written as JSON for the DeepEval suite ({@code evals/}). The harness does not
 * score; {@code expect} is the scenario's expectations, copied as they are. Bump {@link #VERSION} on any breaking
 * change of this shape (the Python loader checks it).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Transcript(int version, ScenarioInfo scenario, int repetition, Instant startedAt, long durationMs,
                         JsonNode expect, Long conversationId, List<TurnRecord> turns, List<RunDigest> runs,
                         Usage usage, WorldSnapshot world, List<String> unexpectedInteractions,
                         List<String> harnessErrors) {

    public static final int VERSION = 1;

    public record ScenarioInfo(String id, String description, List<String> tags, String actor, String actorRole,
                               String promptVersion, String model) {
    }

    /**
     * One user turn.
     *
     * @param abandonedPausedRun the message was sent while the previous run waited for an answer
     * @param segments           the run's stretches in order: each ends with a pause (interaction) or the end
     * @param unusedAnswers      scripted answers no pause asked for
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TurnRecord(int index, String user, boolean whilePaused, boolean abandonedPausedRun,
                             List<SegmentRecord> segments, ReplyRecord reply, ErrorRecord error, boolean endedPaused,
                             List<JsonNode> unusedAnswers) {
    }

    /** A stretch of a run: the tool calls the model made in it and, when it paused, the interaction. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SegmentRecord(int index, UUID runId, List<ToolCallRecord> toolCalls, InteractionRecord interaction) {
    }

    /**
     * A tool call of the model.
     *
     * @param status {@code executed | failed | denied | blocked} (precheck, before the approval) {@code | not_executed}
     *               (validation, loop guard, budget) {@code | asked} (an ask tool) {@code | unknown}
     * @param result the text the model got back for it (null if the run ended before it was fed back)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ToolCallRecord(String id, String name, Map<String, Object> args, String status, String result) {
    }

    /**
     * A pause and the harness's reaction.
     *
     * @param type     {@code approval | choice | input | yesNo}
     * @param tool     the tool call it belongs to (the sensitive tool, or the ask tool)
     * @param answer   the {@code HumanAnswer} sent (JSON with {@code type}); null when left paused
     * @param expected whether a scripted answer fitted it
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InteractionRecord(String type, String callId, String tool, Map<String, Object> args, String prompt,
                                    String detail, JsonNode options, JsonNode answer, boolean expected,
                                    String mismatch, boolean leftPaused) {
    }

    /**
     * The turn's reply (the run's final event).
     *
     * @param status          {@code ANSWERED | REFUSED | FAILED | STOPPED} (envelope)
     * @param response        the structured {@code AgentResponse} the model returned (null for system replies)
     * @param displayedTool   the tool whose result the reply shows, if any
     * @param block           the envelope's block, with its {@code type}
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ReplyRecord(String status, String text, JsonNode response, String displayedTool,
                              List<String> selectedItemIdentifiers, String blockType, JsonNode block,
                              JsonNode suggestedReplies) {
    }

    public record ErrorRecord(String code, String message) {
    }

    /** One run's trace, compacted: how it ended, validation rejections, guard decisions, tool errors. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RunDigest(UUID runId, String status, String errorCode, int agentModelCalls, int guardModelCalls,
                            List<StepRecord> validation, List<StepRecord> guards, List<StepRecord> toolSteps) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record StepRecord(String name, String args, String result, String errorCode) {
    }

    /** Model calls of the whole scenario by purpose, from the recording wrapper. */
    public record Usage(int agentCalls, int guardCalls, int summaryCalls, int failedCalls, long inputTokens,
                        long outputTokens, long modelLatencyMs) {
    }

    /** The world after the last turn and every write the tools asked for, in order. */
    public record WorldSnapshot(EvalWorld state, List<WorldWrite> writes) {
    }
}
