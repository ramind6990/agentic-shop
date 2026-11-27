package com.rmrx.agenticshop.eval.harness;

import com.rmrx.agenticshop.ai.conversation.ConversationService;
import com.rmrx.agenticshop.ai.conversation.message.ConversationMessage;
import com.rmrx.agenticshop.app.security.AccessScope;
import com.rmrx.agenticshop.app.security.CurrentUser;
import com.rmrx.agenticshop.eval.scenario.Scenario;
import com.rmrx.agenticshop.eval.scenario.ScenarioLoader;
import com.rmrx.agenticshop.eval.scenario.Turn;
import com.rmrx.agenticshop.eval.transcript.Transcript;
import com.rmrx.agenticshop.eval.transcript.TranscriptBuilder;
import com.rmrx.agenticshop.eval.world.DomainMocks;
import com.rmrx.agenticshop.eval.world.WorldContext;
import com.rmrx.agenticshop.eval.world.WorldLoader;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs one scenario end to end against the real agent (real model, real conversation service and graph, real guards)
 * with the domain mocked by the scenario's world, and returns its transcript. Harness problems (a timeout, a pause
 * loop, an unstubbed domain call, an inconsistent script) are listed in {@code harnessErrors}; scoring is not done here.
 */
public final class ScenarioRunner {

    private final ConversationService conversations;
    private final TurnDriver driver;
    private final RecordingChatModel recorder;
    private final EvalActors actors;
    private final DomainMocks mocks;
    private final AccessScope scope;
    private final TranscriptBuilder transcripts;

    public ScenarioRunner(ConversationService conversations, RunAwaiter awaiter, RecordingChatModel recorder,
                          EvalActors actors, DomainMocks mocks, AccessScope scope, TranscriptBuilder transcripts,
                          int maxPauses) {
        this.conversations = conversations;
        this.driver = new TurnDriver(conversations, awaiter, recorder, maxPauses);
        this.recorder = recorder;
        this.actors = actors;
        this.mocks = mocks;
        this.scope = scope;
        this.transcripts = transcripts;
    }

    public Transcript run(String scenarioId, int repetition) {
        Scenario scenario = ScenarioLoader.load(scenarioId);
        Instant startedAt = Instant.now();
        CurrentUser user = actors.user(scenario.actor());
        WorldContext world = WorldLoader.prepare(scenario.world(), scenario.actor(), actors::id, startedAt, scope);
        mocks.install(world);
        int mark = recorder.mark();
        List<String> errors = new ArrayList<>();
        List<TurnLog> turns = playTurns(scenario, user, errors);
        mocks.unstubbedCalls().forEach(call -> errors.add("unstubbed domain call: " + call));
        Long conversationId = turns.isEmpty() ? null : turns.getFirst().conversationId();
        List<ConversationMessage> timeline = conversationId == null ? List.of()
                : conversations.timeline(user.id(), conversationId);
        return transcripts.build(new TranscriptBuilder.Inputs(scenario, repetition, user.role().name(), startedAt,
                turns, recorder.since(mark), timeline, world, errors));
    }

    private List<TurnLog> playTurns(Scenario scenario, CurrentUser user, List<String> errors) {
        List<TurnLog> turns = new ArrayList<>();
        Long conversationId = null;
        boolean paused = false;
        for (int i = 0; i < scenario.turns().size(); i++) {
            Turn turn = scenario.turns().get(i);
            if (turn.whilePaused() != paused) {
                errors.add("turn " + i + ": whilePaused is " + turn.whilePaused() + " but the previous run "
                        + (paused ? "is" : "is not") + " paused");
            }
            try {
                TurnLog log = driver.play(i, turn, user, conversationId, paused);
                turns.add(log);
                conversationId = log.conversationId();
                paused = log.endedPaused();
                if (log.harnessError() != null) {
                    errors.add("turn " + i + ": " + log.harnessError());
                    break;
                }
            } catch (RuntimeException e) {
                errors.add("turn " + i + ": " + e);
                break;
            }
        }
        return turns;
    }
}
