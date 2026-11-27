package com.rmrx.agenticshop.eval.scenario;

import com.rmrx.agenticshop.eval.world.EvalWorld;
import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * One eval scenario, read from a YAML file under {@code eval/scenarios}: who talks to the agent, the world the mocked
 * domain services answer from, the user's turns with the answers to give when the run pauses, and the expectations.
 * <p>
 * The harness does not score: {@code expect} is copied as it is into the transcript, and the DeepEval suite
 * ({@code evals/}) interprets it. A scenario is read again for every repetition, so each one starts from a fresh world.
 *
 * @param id     path-like id, e.g. {@code orders/cancel-pending-approve} (the file path without {@code .yaml})
 * @param actor  username of a seeded login ({@code customer_1}, {@code support_t1_1} …) the agent works for
 * @param expect free-form expectations for the scorers
 */
public record Scenario(String id, String description, List<String> tags, String actor, EvalWorld world,
                       List<Turn> turns, JsonNode expect) {

    public Scenario {
        tags = tags == null ? List.of() : List.copyOf(tags);
        world = world == null ? new EvalWorld() : world;
        turns = turns == null ? List.of() : List.copyOf(turns);
        if (actor == null || actor.isBlank()) throw new IllegalArgumentException("scenario " + id + ": actor is required");
        if (turns.isEmpty()) throw new IllegalArgumentException("scenario " + id + ": at least one turn is required");
    }

    Scenario withId(String newId) {
        return new Scenario(newId, description, tags, actor, world, turns, expect);
    }
}
