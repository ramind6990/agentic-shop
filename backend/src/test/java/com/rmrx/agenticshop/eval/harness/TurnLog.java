package com.rmrx.agenticshop.eval.harness;

import com.rmrx.agenticshop.ai.contract.interaction.HumanAnswer;
import com.rmrx.agenticshop.ai.events.ChatResponse;
import com.rmrx.agenticshop.ai.graph.agent.pending.PendingInteraction;
import com.rmrx.agenticshop.eval.scenario.ScriptedAnswer;
import com.rmrx.agenticshop.eval.scenario.Turn;

import java.util.List;
import java.util.UUID;

/**
 * What happened during one turn, as the harness saw it (raw; {@code TranscriptBuilder} turns it into the transcript).
 *
 * @param abandonedPausedRun the message was sent while the previous run was paused (the service abandoned it)
 * @param endedPaused        the turn ended with its run still paused (a {@code leave} answer, or a harness stop)
 * @param harnessError       why the harness had to stop this turn, or null
 */
public record TurnLog(int index, Turn turn, Long conversationId, boolean abandonedPausedRun,
                      List<Segment> segments, List<ScriptedAnswer> unusedAnswers, boolean endedPaused,
                      String harnessError) {

    /**
     * One run segment: the model calls made in it, its events, and how it ended. A segment ending in a pause has the
     * {@link Interaction} the harness reacted with.
     */
    public record Segment(int index, UUID runId, List<ModelCallRecord> modelCalls, List<ChatResponse> events,
                          ChatResponse end, Interaction interaction) {
    }

    /**
     * @param call     the model's tool call the pause belongs to (null if it was not recorded)
     * @param answer   what was sent; null = left paused
     * @param mismatch why the pause was unexpected, or null
     */
    public record Interaction(PendingInteraction pending, ToolCallView call, HumanAnswer answer, String mismatch) {
    }
}
