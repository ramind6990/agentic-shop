package com.rmrx.agenticshop.eval.harness;

import com.rmrx.agenticshop.ai.conversation.ConversationService;
import com.rmrx.agenticshop.ai.conversation.RunOutcome;
import com.rmrx.agenticshop.ai.graph.agent.pending.PendingInteraction;
import com.rmrx.agenticshop.app.security.CurrentUser;
import com.rmrx.agenticshop.eval.scenario.ScriptedAnswer;
import com.rmrx.agenticshop.eval.scenario.Turn;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/**
 * Plays one turn against the real conversation service: sends the user's message, follows the run, and answers each
 * pause from the turn's script until the run replies, fails, or is left paused on purpose.
 */
final class TurnDriver {

    private final ConversationService conversations;
    private final RunAwaiter awaiter;
    private final RecordingChatModel recorder;
    private final InteractionResponder responder = new InteractionResponder();
    private final int maxPauses;

    TurnDriver(ConversationService conversations, RunAwaiter awaiter, RecordingChatModel recorder, int maxPauses) {
        this.conversations = conversations;
        this.awaiter = awaiter;
        this.recorder = recorder;
        this.maxPauses = maxPauses;
    }

    /**
     * @param conversationId null for the first turn (the service creates the conversation)
     * @param previousPaused whether the previous turn left its run paused
     */
    TurnLog play(int index, Turn turn, CurrentUser user, Long conversationId, boolean previousPaused) {
        int turnMark = recorder.mark();
        int mark = turnMark;
        RunOutcome started = conversations.sendMessage(user, conversationId, turn.user(), "eval-" + index);
        Long convId = started.conversationId();
        UUID runId = started.runId();
        Deque<ScriptedAnswer> script = new ArrayDeque<>(turn.answers());
        List<TurnLog.Segment> segments = new ArrayList<>();
        long afterSeq = 0;
        while (true) {
            RunSegment segment = awaiter.await(runId, afterSeq);
            List<ModelCallRecord> calls = recorder.since(mark);
            if (!segment.paused()) {
                segments.add(new TurnLog.Segment(segments.size(), runId, calls, segment.events(), segment.end(), null));
                return log(index, turn, convId, previousPaused, segments, script, false, null);
            }
            PendingInteraction pending = segment.end().interaction();
            var call = ToolCallView.find(recorder.since(turnMark), pending.callId());
            InteractionResponder.Decision decision = responder.decide(pending, call, script);
            segments.add(new TurnLog.Segment(segments.size(), runId, calls, segment.events(), segment.end(),
                    new TurnLog.Interaction(pending, call.orElse(null), decision.answer(), decision.mismatch())));
            if (decision.answer() == null) {
                return log(index, turn, convId, previousPaused, segments, script, true, null);
            }
            if (segments.size() > maxPauses) {
                return log(index, turn, convId, previousPaused, segments, script, true,
                        "more than " + maxPauses + " pauses in one turn");
            }
            mark = recorder.mark();
            RunOutcome resumed = conversations.answer(user.id(), convId, pending.interactionId(), decision.answer(),
                    "eval-" + index + "-answer-" + segments.size());
            if (resumed instanceof RunOutcome.Abandoned) {
                return log(index, turn, convId, previousPaused, segments, script, false,
                        "the answer did not resume the run (abandoned): " + decision.answer());
            }
            afterSeq = segment.lastSeq();
        }
    }

    private static TurnLog log(int index, Turn turn, Long convId, boolean previousPaused,
                               List<TurnLog.Segment> segments, Deque<ScriptedAnswer> script, boolean endedPaused,
                               String error) {
        return new TurnLog(index, turn, convId, previousPaused, List.copyOf(segments), List.copyOf(script),
                endedPaused, error);
    }
}
