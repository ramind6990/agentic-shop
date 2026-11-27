package com.rmrx.agenticshop.eval.world;

import com.rmrx.agenticshop.app.common.exception.NotFoundException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A prepared world while a scenario runs: the world itself, its clock, the writes log and the faults, plus lookups
 * that apply the caller's visibility ({@link Visibility}). One per scenario run; the stubs answer from it.
 */
public final class WorldContext {

    private final EvalWorld world;
    private final WorldTime time;
    private final Visibility visibility;
    private final Faults faults;
    private final List<WorldWrite> writes = Collections.synchronizedList(new ArrayList<>());
    private final AtomicLong ids = new AtomicLong(9_000_000);

    WorldContext(EvalWorld world, WorldTime time, Visibility visibility) {
        this.world = world;
        this.time = time;
        this.visibility = visibility;
        this.faults = new Faults(world.faults);
    }

    public EvalWorld world() {
        return world;
    }

    public WorldTime time() {
        return time;
    }

    public Visibility visibility() {
        return visibility;
    }

    public List<WorldWrite> writes() {
        return List.copyOf(writes);
    }

    void write(WorldWrite write) {
        writes.add(write);
    }

    /** Fails the call when the world declares a fault for it. */
    void enter(String call) {
        faults.check(call);
    }

    long nextId() {
        return ids.incrementAndGet();
    }

    /** The order with this number (or id), if the caller may see it. */
    WorldOrder requireOrder(String idOrNumber) {
        return findOrder(idOrNumber)
                .filter(o -> visibility.visible(o.ownerId))
                .orElseThrow(() -> new NotFoundException("Order", idOrNumber));
    }

    Optional<WorldOrder> findOrder(String idOrNumber) {
        String key = idOrNumber == null ? "" : idOrNumber.trim().toUpperCase(Locale.ROOT);
        return world.orders.stream()
                .filter(o -> o.number.equalsIgnoreCase(key) || String.valueOf(o.id).equals(key))
                .findFirst();
    }

    Optional<WorldAddress> address(Long id) {
        return world.addresses.stream().filter(a -> a.id.equals(id)).findFirst();
    }

    WorldTicket requireTicket(String number) {
        String key = number == null ? "" : number.trim();
        return world.tickets.stream()
                .filter(t -> t.number.equalsIgnoreCase(key))
                .filter(t -> visibility.visible(t.ownerId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Ticket", number));
    }
}
