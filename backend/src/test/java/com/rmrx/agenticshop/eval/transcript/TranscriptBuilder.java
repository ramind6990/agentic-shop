package com.rmrx.agenticshop.eval.transcript;

import com.rmrx.agenticshop.ai.conversation.message.ConversationMessage;
import com.rmrx.agenticshop.ai.events.ChatResponse;
import com.rmrx.agenticshop.ai.graph.agent.pending.PendingInteraction;
import com.rmrx.agenticshop.ai.trace.TraceQueries;
import com.rmrx.agenticshop.eval.harness.ModelCallRecord;
import com.rmrx.agenticshop.eval.harness.ModelCallRecord.Purpose;
import com.rmrx.agenticshop.eval.harness.TurnLog;
import com.rmrx.agenticshop.eval.scenario.Scenario;
import com.rmrx.agenticshop.eval.world.WorldContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Turns the harness's raw logs of one scenario run into its {@link Transcript}. */
public final class TranscriptBuilder {

    private final JsonMapper json = JsonMapper.builder().build();
    private final ReplyReader replies = new ReplyReader(json);
    private final TraceDigests traces;
    private final String promptVersion;
    private final String model;

    public TranscriptBuilder(TraceQueries traces, String promptVersion, String model) {
        this.traces = new TraceDigests(traces);
        this.promptVersion = promptVersion;
        this.model = model;
    }

    /** Everything a scenario run produced; {@code harnessErrors} already holds the runner's own findings. */
    public record Inputs(Scenario scenario, int repetition, String actorRole, Instant startedAt, List<TurnLog> turns,
                         List<ModelCallRecord> modelCalls, List<ConversationMessage> timeline, WorldContext world,
                         List<String> harnessErrors) {
    }

    public Transcript build(Inputs in) {
        Map<String, String> results = results(in.modelCalls());
        List<String> unexpected = new ArrayList<>();
        List<Transcript.TurnRecord> turns = in.turns().stream()
                .map(t -> turn(t, results, in.timeline(), unexpected))
                .toList();
        Scenario s = in.scenario();
        Long conversationId = in.turns().isEmpty() ? null : in.turns().getFirst().conversationId();
        return new Transcript(Transcript.VERSION,
                new Transcript.ScenarioInfo(s.id(), s.description(), s.tags(), s.actor(), in.actorRole(),
                        promptVersion, model),
                in.repetition(), in.startedAt(), Duration.between(in.startedAt(), Instant.now()).toMillis(),
                s.expect(), conversationId, turns, runs(in.turns()), usage(in.modelCalls()),
                new Transcript.WorldSnapshot(in.world().world(), in.world().writes()), unexpected, in.harnessErrors());
    }

    private Transcript.TurnRecord turn(TurnLog log, Map<String, String> results, List<ConversationMessage> timeline,
                                       List<String> unexpected) {
        List<List<Transcript.ToolCallRecord>> calls = ToolCallStatuses.of(log.segments(), results);
        List<Transcript.SegmentRecord> segments = new ArrayList<>();
        for (TurnLog.Segment segment : log.segments()) {
            Transcript.InteractionRecord interaction = segment.interaction() == null ? null
                    : interaction(segment.interaction());
            if (interaction != null && !interaction.expected()) {
                unexpected.add("turn " + log.index() + ": " + interaction.mismatch());
            }
            segments.add(new Transcript.SegmentRecord(segment.index(), segment.runId(), calls.get(segment.index()),
                    interaction));
        }
        ChatResponse end = log.segments().isEmpty() ? null : log.segments().getLast().end();
        Transcript.ReplyRecord reply = end != null && end.type() == ChatResponse.Type.REPLY
                ? replies.reply(end, timeline) : null;
        Transcript.ErrorRecord error = end != null && end.type() == ChatResponse.Type.ERROR ? replies.error(end) : null;
        List<JsonNode> unused = log.unusedAnswers().stream().map(a -> (JsonNode) json.valueToTree(a)).toList();
        return new Transcript.TurnRecord(log.index(), log.turn().user(), log.turn().whilePaused(),
                log.abandonedPausedRun(), segments, reply, error, log.endedPaused(), unused);
    }

    private Transcript.InteractionRecord interaction(TurnLog.Interaction i) {
        PendingInteraction p = i.pending();
        JsonNode node = json.valueToTree(p);
        String detail = p instanceof PendingInteraction.ApprovalRequest a ? a.detail() : null;
        JsonNode options = p instanceof PendingInteraction.ChoiceQuestion q ? json.valueToTree(q.options()) : null;
        return new Transcript.InteractionRecord(node.get("type").asString(), p.callId(),
                i.call() == null ? null : i.call().name(), i.call() == null ? null : i.call().args(), p.promptText(),
                detail, options, i.answer() == null ? null : json.valueToTree(i.answer()), i.mismatch() == null,
                i.mismatch(), i.answer() == null);
    }

    private List<Transcript.RunDigest> runs(List<TurnLog> turns) {
        LinkedHashSet<UUID> runIds = new LinkedHashSet<>();
        turns.forEach(t -> t.segments().forEach(s -> runIds.add(s.runId())));
        return runIds.stream().map(traces::digest).toList();
    }

    private static Map<String, String> results(List<ModelCallRecord> calls) {
        Map<String, String> results = new HashMap<>();
        calls.forEach(c -> c.toolResults().forEach(r -> results.put(r.id(), r.text())));
        return results;
    }

    private static Transcript.Usage usage(List<ModelCallRecord> calls) {
        return new Transcript.Usage(count(calls, Purpose.AGENT), count(calls, Purpose.GUARD),
                count(calls, Purpose.SUMMARY), (int) calls.stream().filter(c -> c.error() != null).count(),
                calls.stream().mapToLong(ModelCallRecord::inputTokens).sum(),
                calls.stream().mapToLong(ModelCallRecord::outputTokens).sum(),
                calls.stream().mapToLong(ModelCallRecord::latencyMs).sum());
    }

    private static int count(List<ModelCallRecord> calls, Purpose purpose) {
        return (int) calls.stream().filter(c -> c.purpose() == purpose && c.error() == null).count();
    }
}
