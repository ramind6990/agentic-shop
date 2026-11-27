package com.rmrx.agenticshop.eval.world;

import com.rmrx.agenticshop.app.security.AccessScope;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Turns the scenario's {@code world:} section into a ready world: owners resolved to user ids, relative times to
 * instants, missing ids and defaults filled in, and every customer with an order or ticket given a default address.
 */
public final class WorldLoader {

    private WorldLoader() {
    }

    /**
     * @param actor   username the scenario runs as (owner of rows without {@code owner})
     * @param userIds username → user id of the seeded logins
     */
    public static WorldContext prepare(EvalWorld world, String actor, Function<String, Long> userIds, Instant now,
                                       AccessScope scope) {
        WorldTime time = new WorldTime(now);
        Function<String, Long> ownerId = owner -> userIds.apply(owner == null ? actor : owner);
        prepareAddresses(world, ownerId);
        prepareOrders(world, ownerId, time);
        prepareTickets(world, ownerId, time);
        return new WorldContext(world, time, new Visibility(scope));
    }

    private static void prepareAddresses(EvalWorld world, Function<String, Long> ownerId) {
        long next = 500;
        for (WorldAddress a : world.addresses) {
            a.ownerId = ownerId.apply(a.owner);
            if (a.id == null) a.id = ++next;
        }
        Set<Long> customers = new LinkedHashSet<>();
        world.orders.forEach(o -> customers.add(ownerId.apply(o.owner)));
        world.tickets.forEach(t -> customers.add(ownerId.apply(t.owner)));
        for (Long customer : customers) {
            if (world.addresses.stream().noneMatch(a -> a.ownerId.equals(customer))) {
                WorldAddress home = new WorldAddress();
                home.id = 900 + customer % 100;
                home.ownerId = customer;
                home.isDefault = true;
                world.addresses.add(home);
            }
        }
        for (Long customer : world.addresses.stream().map(a -> a.ownerId).distinct().toList()) {
            List<WorldAddress> own = world.addresses.stream().filter(a -> a.ownerId.equals(customer)).toList();
            if (own.stream().noneMatch(a -> a.isDefault)) own.getFirst().isDefault = true;
        }
        world.addresses.forEach(a -> {
            if (a.recipient == null) a.recipient = "Alex Morgan";
        });
    }

    private static void prepareOrders(EvalWorld world, Function<String, Long> ownerId, WorldTime time) {
        for (int i = 0; i < world.orders.size(); i++) {
            WorldOrder o = world.orders.get(i);
            Objects.requireNonNull(o.number, "every order needs a number");
            o.number = o.number.trim().toUpperCase();
            o.id = o.id == null ? 7000L + i : o.id;
            o.ownerId = ownerId.apply(o.owner);
            o.placedAt = time.at(o.placed);
            o.payment.id = o.payment.id == null ? o.id * 10 : o.payment.id;
            for (int j = 0; j < o.items.size(); j++) {
                WorldItem item = o.items.get(j);
                if (item.itemId == null) item.itemId = o.id * 10 + j + 1;
                if (item.productId == null) item.productId = 1000L + i * 10 + j;
                if (item.title == null) item.title = "Item " + (j + 1);
                if (item.sku == null) item.sku = "SKU-" + item.productId;
            }
            for (int j = 0; j < o.shipments.size(); j++) {
                WorldShipment s = o.shipments.get(j);
                if (s.id == null) s.id = o.id * 10 + j + 1;
                if (s.trackingNumber == null) s.trackingNumber = "1ZEVAL" + s.id;
            }
            if (o.shipTo == null) {
                o.shipTo = world.addresses.stream().filter(a -> a.ownerId.equals(o.ownerId) && a.isDefault)
                        .findFirst().orElseThrow().id;
            }
        }
    }

    private static void prepareTickets(EvalWorld world, Function<String, Long> ownerId, WorldTime time) {
        for (int i = 0; i < world.tickets.size(); i++) {
            WorldTicket t = world.tickets.get(i);
            t.id = t.id == null ? 8000L + i : t.id;
            if (t.number == null) t.number = "TCK-%07d".formatted(t.id);
            t.ownerId = ownerId.apply(t.owner);
            t.createdAt = time.at(t.created);
            Instant last = t.createdAt;
            for (WorldMessage m : t.messages) {
                m.createdAt = m.at == null ? t.createdAt : time.at(m.at);
                if (m.createdAt.isAfter(last)) last = m.createdAt;
            }
            t.updatedAt = last;
        }
    }
}
