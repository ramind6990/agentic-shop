package com.rmrx.agenticshop.ai.tools.mock;

import com.rmrx.agenticshop.ai.context.AgentContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One {@link MockWorld} per userId, built lazily from that user's scenario (the default unless one was set), so
 * writes persist across calls. {@link #setScenario} rebuilds the world; {@link #reset} drops it and the override, so
 * the next call starts from the default scenario again.
 */
public class MockWorlds {

    private final String defaultScenario;
    private final Map<Long, MockWorld> worlds = new ConcurrentHashMap<>();
    private final Map<Long, String> scenarios = new ConcurrentHashMap<>();

    public MockWorlds(String defaultScenario) {
        if (!MockScenarios.exists(defaultScenario)) {
            throw new IllegalStateException("app.ai.mock.default-scenario '" + defaultScenario
                    + "' is not one of " + MockScenarios.ALL);
        }
        this.defaultScenario = defaultScenario;
    }

    public MockWorld worldFor(AgentContext ctx) {
        return worlds.computeIfAbsent(ctx.userId(), id -> MockScenarios.build(scenarioOf(id), ctx));
    }

    public String scenarioOf(Long userId) {
        return scenarios.getOrDefault(userId, defaultScenario);
    }

    /** Switches the caller to a scenario and rebuilds their world from scratch. */
    public MockWorld setScenario(AgentContext ctx, String scenario) {
        if (!MockScenarios.exists(scenario)) {
            throw new IllegalArgumentException("Unknown scenario '" + scenario + "'; one of " + MockScenarios.ALL);
        }
        scenarios.put(ctx.userId(), scenario);
        MockWorld world = MockScenarios.build(scenario, ctx);
        worlds.put(ctx.userId(), world);
        return world;
    }

    /** Switches only if the caller is on another scenario, keeping the state of the current one otherwise. */
    public MockWorld ensureScenario(AgentContext ctx, String scenario) {
        MockWorld current = worlds.get(ctx.userId());
        if (current != null && current.scenario().equals(scenario)) return current;
        return setScenario(ctx, scenario);
    }

    public void reset(Long userId) {
        scenarios.remove(userId);
        worlds.remove(userId);
    }

    public String defaultScenario() {
        return defaultScenario;
    }
}
