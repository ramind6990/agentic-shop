package com.rmrx.agenticshop.ai.tools.mock;

import com.rmrx.agenticshop.ai.contract.tool.ShipmentView;
import com.rmrx.agenticshop.order.entity.Carrier;
import com.rmrx.agenticshop.order.entity.OrderStatus;
import com.rmrx.agenticshop.order.entity.PaymentMethod;
import com.rmrx.agenticshop.order.entity.ShipmentStatus;
import com.rmrx.agenticshop.returns.entity.RefundStatus;
import com.rmrx.agenticshop.returns.entity.ReturnReason;
import com.rmrx.agenticshop.returns.entity.ReturnStatus;
import com.rmrx.agenticshop.support.entity.AuthorType;
import com.rmrx.agenticshop.support.entity.SupportTier;
import com.rmrx.agenticshop.support.entity.TicketStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * One subject customer's in-memory backend: orders, items, shipments, products, returns, refunds and tickets built from a
 * scenario. Mutable (writes really change it) and thread-safe: every read and write goes through {@link #locked}.
 * The seeded {@link #random()} drives failure injection, so runs against a fresh world are reproducible.
 */
public final class MockWorld {

    private final String scenario;
    private final Long customerId;
    private final ReentrantLock lock = new ReentrantLock();
    private final Random random;

    /** Kinds of rows writes create; each has its own id range above every fixture id, so ids never collide. */
    enum IdKind {
        RETURN(910_001),
        REFUND(920_001),
        MESSAGE(930_001);

        final long first;

        IdKind(long first) {
            this.first = first;
        }
    }

    private final Map<IdKind, AtomicLong> nextIds = new EnumMap<>(IdKind.class);

    final Map<Long, Product> products = new LinkedHashMap<>();
    final Map<Long, Order> orders = new LinkedHashMap<>();
    final Map<Long, Shipment> shipments = new LinkedHashMap<>();
    final Map<Long, Return> returns = new LinkedHashMap<>();
    final Map<Long, Refund> refunds = new LinkedHashMap<>();
    final Map<Long, Ticket> tickets = new LinkedHashMap<>();

    MockWorld(String scenario, Long customerId) {
        this.scenario = scenario;
        this.customerId = customerId;
        this.random = new Random(31L * scenario.hashCode() + customerId);
        for (IdKind kind : IdKind.values()) nextIds.put(kind, new AtomicLong(kind.first));
    }

    public String scenario() {
        return scenario;
    }

    /** The subject customer this world belongs to. */
    public Long customerId() {
        return customerId;
    }

    /** Seeded per world; only use it inside {@link #locked} or for injection decisions. */
    public double nextRandom() {
        lock.lock();
        try {
            return random.nextDouble();
        } finally {
            lock.unlock();
        }
    }

    public <T> T locked(Supplier<T> action) {
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    long newId(IdKind kind) {
        return nextIds.get(kind).getAndIncrement();
    }

    // ---- rows (package-private, mutated only under the lock) ----

    static final class Product {
        Long id;
        String sku;
        String title;
        String category;
        String brand;
        Long sellerId;
        String sellerName;
        BigDecimal price;
        BigDecimal listPrice;
        BigDecimal rating;
        int ratingCount;
        int available;
        LocalDate restockEta;
        String description;
        Map<String, String> attributes = new LinkedHashMap<>();
    }

    static final class Order {
        Long id;
        String number;
        Long customerId;
        OrderStatus status;
        Instant placedAt;
        BigDecimal shippingFee = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        String currency = "USD";
        PaymentMethod paymentMethod = PaymentMethod.CARD;
        /** What the customer actually paid; zero while PENDING. */
        BigDecimal paidAmount = BigDecimal.ZERO;
        final List<Item> items = new ArrayList<>();

        BigDecimal subtotal() {
            return items.stream().map(Item::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        BigDecimal total() {
            return subtotal().add(shippingFee).add(tax).setScale(2, RoundingMode.HALF_UP);
        }
    }

    static final class Item {
        Long id;
        Long orderId;
        Long productId;
        String title;
        String sku;
        Long sellerId;
        BigDecimal unitPrice;
        int qty;
        Long shipmentId;

        BigDecimal lineTotal() {
            return unitPrice.multiply(BigDecimal.valueOf(qty));
        }
    }

    static final class Shipment {
        Long id;
        Long orderId;
        Carrier carrier;
        String trackingNumber;
        ShipmentStatus status;
        Instant shippedAt;
        Instant deliveredAt;
        LocalDate eta;
        /** Newest first. */
        final List<ShipmentView.Event> events = new ArrayList<>();
    }

    static final class Return {
        Long id;
        Long orderId;
        Long itemId;
        int qty;
        ReturnReason reason;
        ReturnStatus status;
        Instant openedAt;
        Instant resolvedAt;
        String note;
    }

    static final class Refund {
        Long id;
        Long orderId;
        Long returnId;
        BigDecimal amount;
        RefundStatus status;
        PaymentMethod method;
        Instant createdAt;
        String reason;
    }

    static final class Ticket {
        Long id;
        Long customerId;
        String subject;
        TicketStatus status;
        SupportTier tier;
        final List<Message> messages = new ArrayList<>();
    }

    static final class Message {
        Long id;
        AuthorType authorType;
        String author;
        String body;
        boolean internal;
        Instant at;
    }
}
