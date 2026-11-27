package com.rmrx.agenticshop.eval.world;

import java.util.ArrayList;
import java.util.List;

/**
 * The scenario's world: the rows the mocked domain services answer from (the {@code world:} section of a scenario).
 * Mutable on purpose: writes (a cancellation, an address change, a new ticket) change it, and its final state goes into
 * the transcript for the world-state check. Rows belong to the scenario's actor unless {@code owner} names another
 * seeded login.
 */
public class EvalWorld {

    public List<WorldAddress> addresses = new ArrayList<>();
    public List<WorldOrder> orders = new ArrayList<>();
    public List<WorldTicket> tickets = new ArrayList<>();
    /** Calls that fail on purpose, e.g. {@code {call: ShipmentService.track, error: timeout, times: 2}}. */
    public List<Fault> faults = new ArrayList<>();
}
