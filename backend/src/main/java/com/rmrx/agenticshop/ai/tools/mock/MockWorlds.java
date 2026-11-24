package com.rmrx.agenticshop.ai.tools.mock;

import com.rmrx.agenticshop.ai.context.AgentContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * One {@link MockWorld} per subject customer, built lazily from that world's scenario (the default unless one was
 * set), so writes persist across calls and are seen by everyone acting for that customer.
 *
 * <p>The subject customer of a call: a CUSTOMER is their own subject; any other role acting in a ticket context uses
 * the real ticket's customer (looked up once per ticket); without a ticket, or for an unknown ticket, the subject is
 * {@link MockScenarios#PRIMARY_CUSTOMER_ID}. So a support agent's write in a customer's ticket changes the world that
 * customer sees. A SELLER gets its own variant of the subject's world (the catalog's "caller seller" products belong
 * to it); everyone else shares the variant owned by {@link MockScenarios#DEFAULT_SELLER_ID}.
 *
 * <p>{@link #setScenario} rebuilds the caller's world; {@link #reset} drops it and its override, so the next call
 * starts from the default scenario again. Ownership rules are unchanged: they stay in the ports.
 */
public class MockWorlds {

    /** Which world a call runs against: the subject customer and the seller owning the "caller seller" products. */
    public record WorldKey(long customerId, long sellerId) {
    }

    private final String defaultScenario;
    /** ticketId to the ticket's customer id, or null if the ticket does not exist. */
    private final Function<Long, Long> ticketCustomer;
    private final Map<WorldKey, MockWorld> worlds = new ConcurrentHashMap<>();
    private final Map<WorldKey, String> scenarios = new ConcurrentHashMap<>();
    private final Map<Long, Long> ticketCustomers = new ConcurrentHashMap<>();

    public MockWorlds(String defaultScenario) {
        this(defaultScenario, ticketId -> null);
    }

    public MockWorlds(String defaultScenario, Function<Long, Long> ticketCustomer) {
        if (!MockScenarios.exists(defaultScenario)) {
            throw new IllegalStateException("app.ai.mock.default-scenario '" + defaultScenario
                    + "' is not one of " + MockScenarios.ALL);
        }
        this.defaultScenario = defaultScenario;
        this.ticketCustomer = ticketCustomer;
    }

    public WorldKey keyOf(AgentContext ctx) {
        long seller = ctx.isSeller() && ctx.sellerId() != null ? ctx.sellerId() : MockScenarios.DEFAULT_SELLER_ID;
        return new WorldKey(subjectCustomer(ctx), seller);
    }

    /** The customer whose world this call runs against. */
    public long subjectCustomer(AgentContext ctx) {
        if (ctx.isCustomer()) return ctx.userId();
        if (ctx.ticketId() != null) {
            Long customer = ticketCustomers.get(ctx.ticketId());
            if (customer == null) {
                customer = ticketCustomer.apply(ctx.ticketId());
                if (customer != null) ticketCustomers.put(ctx.ticketId(), customer);
            }
            if (customer != null) return customer;
        }
        return MockScenarios.PRIMARY_CUSTOMER_ID;
    }

    public MockWorld worldFor(AgentContext ctx) {
        WorldKey key = keyOf(ctx);
        return worlds.computeIfAbsent(key, k -> MockScenarios.build(scenarioOf(k), k));
    }

    public String scenarioOf(AgentContext ctx) {
        return scenarioOf(keyOf(ctx));
    }

    private String scenarioOf(WorldKey key) {
        return scenarios.getOrDefault(key, defaultScenario);
    }

    /** Switches the caller's world to a scenario and rebuilds it from scratch. */
    public MockWorld setScenario(AgentContext ctx, String scenario) {
        if (!MockScenarios.exists(scenario)) {
            throw new IllegalArgumentException("Unknown scenario '" + scenario + "'; one of " + MockScenarios.ALL);
        }
        WorldKey key = keyOf(ctx);
        scenarios.put(key, scenario);
        MockWorld world = MockScenarios.build(scenario, key);
        worlds.put(key, world);
        return world;
    }

    /** Switches only if the caller's world is on another scenario, keeping the state of the current one otherwise. */
    public MockWorld ensureScenario(AgentContext ctx, String scenario) {
        MockWorld current = worlds.get(keyOf(ctx));
        if (current != null && current.scenario().equals(scenario)) return current;
        return setScenario(ctx, scenario);
    }

    /** Drops the caller's world and its scenario override. */
    public void reset(AgentContext ctx) {
        WorldKey key = keyOf(ctx);
        scenarios.remove(key);
        worlds.remove(key);
    }

    public String defaultScenario() {
        return defaultScenario;
    }
}
