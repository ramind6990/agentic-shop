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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The scenarios, as Java builders rather than JSON fixtures: every world is built relative to the caller (their
 * customer or seller id) and to "now" (delivered 5 days ago), which plain JSON would need a placeholder language for.
 *
 * <p>Fixed ids, the same in every world: products 7101-7116, orders 50001-50006 (50006 belongs to
 * {@link #OTHER_CUSTOMER_ID}), tickets 40001 (the subject's) and 40002 (the other customer's). The orders belong
 * to the world's subject customer (see {@link MockWorlds}); for a non-seller caller "the caller's seller" is
 * {@link #DEFAULT_SELLER_ID}.
 */
public final class MockScenarios {

    public static final String HAPPY = "happy";
    public static final String EMPTY = "empty";
    public static final String PARTIAL_SHIPMENT = "partial-shipment";
    public static final String ALREADY_REFUNDED = "already-refunded";
    public static final String INELIGIBLE = "ineligible";
    /** happy plus two cancellable orders of the same headphones (50007 PAID, 50008 PENDING): clarification tests. */
    public static final String AMBIGUOUS = "ambiguous";
    /** happy data, but every port call fails with UNAVAILABLE: repeated-failure (auto-escalation) tests. */
    public static final String OUTAGE = "outage";
    public static final Set<String> ALL = Set.of(HAPPY, EMPTY, PARTIAL_SHIPMENT, ALREADY_REFUNDED, INELIGIBLE,
            AMBIGUOUS, OUTAGE);

    /** Owner of the orders when the caller is not a customer (staff look it up with find_customer_orders). */
    public static final long PRIMARY_CUSTOMER_ID = 7001;
    /** Owner of order 50006 and ticket 40002, to test not-owner access. */
    public static final long OTHER_CUSTOMER_ID = 7999;
    /** "The caller's seller" when the caller is not a seller. */
    public static final long DEFAULT_SELLER_ID = 301;
    public static final long CALLER_TICKET_ID = 40001;
    public static final long OTHER_TICKET_ID = 40002;

    private static final long HOME_SELLER = 302;
    private static final long APPAREL_SELLER = 303;
    private static final long GADGET_SELLER = 304;
    private static final BigDecimal TAX_RATE = new BigDecimal("0.08");
    private static final BigDecimal FREE_SHIPPING_FROM = new BigDecimal("50");

    /** Category slug to parent slug, as in the seeded catalog tree. */
    static final Map<String, String> CATEGORY_PARENT = Map.ofEntries(
            Map.entry("headphones", "audio"), Map.entry("earbuds", "audio"), Map.entry("speakers", "audio"),
            Map.entry("audio", "electronics"), Map.entry("monitors", "computers"),
            Map.entry("keyboards", "computers"), Map.entry("mice", "computers"),
            Map.entry("computers", "electronics"), Map.entry("chargers", "phones-tablets"),
            Map.entry("phones-tablets", "electronics"), Map.entry("coffee-makers", "small-appliances"),
            Map.entry("air-fryers", "small-appliances"), Map.entry("small-appliances", "home-kitchen"),
            Map.entry("skillets", "cookware"), Map.entry("cookware", "home-kitchen"),
            Map.entry("sheet-sets", "bedding"), Map.entry("bedding", "home-kitchen"),
            Map.entry("mens-jackets", "mens-clothing"), Map.entry("mens-tshirts", "mens-clothing"),
            Map.entry("mens-clothing", "fashion"), Map.entry("running-shoes", "shoes"),
            Map.entry("shoes", "fashion"));

    private MockScenarios() {
    }

    public static boolean exists(String scenario) {
        return scenario != null && ALL.contains(scenario);
    }

    public static MockWorld build(String scenario, MockWorlds.WorldKey key) {
        if (!exists(scenario)) throw new IllegalArgumentException("Unknown mock scenario: " + scenario);
        Builder b = new Builder(new MockWorld(scenario, key.customerId()), key);
        b.catalog();
        switch (scenario) {
            case EMPTY -> b.ticket(false);
            case HAPPY -> b.happy(5, false, false);
            case PARTIAL_SHIPMENT -> b.happy(5, true, false);
            case ALREADY_REFUNDED -> b.happy(5, false, true);
            case INELIGIBLE -> b.happy(45, false, false);
            case AMBIGUOUS -> {
                b.happy(5, false, false);
                b.ambiguousHeadphones();
            }
            case OUTAGE -> b.happy(5, false, false);
            default -> throw new IllegalStateException(scenario);
        }
        return b.world;
    }

    private static final class Builder {

        final MockWorld world;
        final long customerId;
        final long callerSeller;
        final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        long itemSeq = 60001;
        long shipmentSeq = 70001;
        long returnSeq = 80001;
        long refundSeq = 85001;
        long messageSeq = 45001;

        Builder(MockWorld world, MockWorlds.WorldKey key) {
            this.world = world;
            this.customerId = key.customerId();
            this.callerSeller = key.sellerId();
        }

        Instant ago(Duration d) {
            return now.minus(d);
        }

        Instant daysAgo(int days) {
            return ago(Duration.ofDays(days));
        }

        // ---------------------------------------------------------------- catalog

        void catalog() {
            String audio = "Northwind Electronics";
            product(7101, "NW-AUR-Q45-BLK", "Auralis Q45 Wireless Noise-Cancelling Over-Ear Headphones", "headphones",
                    "Auralis", callerSeller, audio, "129.99", "149.99", "4.6", 1843, 42,
                    "Hybrid active noise cancelling, 40-hour battery and multipoint Bluetooth 5.3.",
                    Map.of("Color", "Black", "Connectivity", "Bluetooth 5.3", "Battery life", "40 h",
                            "Noise cancelling", "Yes"));
            product(7102, "NW-AUR-STP-WRD", "Auralis Studio Pro Wired Monitoring Headphones", "headphones",
                    "Auralis", callerSeller, audio, "89.00", null, "4.7", 612, 18,
                    "Closed-back studio headphones with 50 mm drivers and a detachable coiled cable.",
                    Map.of("Color", "Black", "Connectivity", "3.5 mm / 6.35 mm wired", "Impedance", "32 ohm"));
            product(7103, "NW-KST-AIR-WHT", "Kestrel Buds Air True Wireless Earbuds", "earbuds", "Kestrel",
                    callerSeller, audio, "59.99", "69.99", "4.3", 2310, 120,
                    "Lightweight earbuds with 6 sizes of ear tips, IPX5 sweat resistance and a wireless charging case.",
                    Map.of("Color", "White", "Water resistance", "IPX5", "Battery life", "7 h (28 h with case)"));
            product(7104, "NW-KST-BOOM2-BLU", "Kestrel Boom 2 Waterproof Bluetooth Speaker", "speakers", "Kestrel",
                    callerSeller, audio, "74.50", null, "4.5", 954, 33,
                    "360-degree sound, IP67 waterproof and dustproof, 16 hours of playback.",
                    Map.of("Color", "Ocean Blue", "Water resistance", "IP67", "Battery life", "16 h"));
            product(7105, "NW-KST-KIDS-PNK", "Kestrel Kids Volume-Limited Headphones", "headphones", "Kestrel",
                    callerSeller, audio, "29.99", null, "4.4", 388, 0,
                    "On-ear headphones for kids, volume limited to 85 dB, foldable with a shareable audio port.",
                    Map.of("Color", "Pink", "Connectivity", "3.5 mm wired", "Max volume", "85 dB"));
            product(7106, "VG-VTX-27-4K", "Vantix 27\" 4K UHD IPS Monitor", "monitors", "Vantix", GADGET_SELLER,
                    "Volt Gadgets", "329.00", "379.00", "4.5", 721, 14,
                    "27-inch 3840x2160 IPS panel, 99% sRGB, USB-C with 65 W power delivery and a height-adjustable stand.",
                    Map.of("Screen size", "27 in", "Resolution", "3840x2160", "Panel", "IPS", "Refresh rate", "60 Hz"));
            product(7107, "VG-VTX-MK3-BRN", "Vantix MK3 Mechanical Keyboard, Brown Switches", "keyboards", "Vantix",
                    GADGET_SELLER, "Volt Gadgets", "99.99", null, "4.6", 1102, 25,
                    "Tenkeyless mechanical keyboard with hot-swappable tactile brown switches and PBT keycaps.",
                    Map.of("Layout", "Tenkeyless (US)", "Switches", "Tactile brown", "Connectivity", "USB-C / Bluetooth"));
            product(7108, "VG-VTX-GLIDE", "Vantix Glide Wireless Ergonomic Mouse", "mice", "Vantix", GADGET_SELLER,
                    "Volt Gadgets", "34.99", null, "4.2", 845, 60,
                    "Silent-click ergonomic mouse with a 4000 DPI sensor and 18-month battery life.",
                    Map.of("Color", "Graphite", "DPI", "4000", "Connectivity", "2.4 GHz / Bluetooth"));
            product(7109, "VG-AMP-65W-GAN", "Ampra 65W USB-C GaN Fast Charger", "chargers", "Ampra", GADGET_SELLER,
                    "Volt Gadgets", "39.99", "49.99", "4.7", 3120, 200,
                    "Compact 3-port GaN charger that powers a laptop, phone and earbuds at once.",
                    Map.of("Output", "65 W", "Ports", "2x USB-C, 1x USB-A", "Foldable plug", "Yes"));
            product(7110, "HH-BRW-12CUP", "Brewline 12-Cup Programmable Coffee Maker", "coffee-makers", "Brewline",
                    HOME_SELLER, "Hearth & Home Co.", "79.99", null, "4.4", 1530, 37,
                    "24-hour programmable brew, strength control and a keep-warm plate with auto shut-off.",
                    Map.of("Capacity", "12 cups", "Color", "Stainless steel", "Programmable", "Yes"));
            product(7111, "HH-IRW-12SKL", "Ironwood 12\" Pre-Seasoned Cast Iron Skillet", "skillets", "Ironwood",
                    HOME_SELLER, "Hearth & Home Co.", "44.95", null, "4.8", 4210, 85,
                    "Heavy cast iron skillet, pre-seasoned with vegetable oil; oven, stovetop and campfire safe.",
                    Map.of("Diameter", "12 in", "Material", "Cast iron", "Oven safe", "Yes"));
            product(7112, "HH-CRS-58QT", "Crispa 5.8 qt Digital Air Fryer", "air-fryers", "Crispa", HOME_SELLER,
                    "Hearth & Home Co.", "119.00", "139.00", "4.5", 2675, 0,
                    "1700 W air fryer with 8 presets, a nonstick dishwasher-safe basket and a shake reminder.",
                    Map.of("Capacity", "5.8 qt", "Power", "1700 W", "Presets", "8"));
            world.products.get(7112L).restockEta = LocalDate.ofInstant(now, ZoneOffset.UTC).plusDays(12);
            product(7113, "HH-CLD-400TC-Q", "Cloudrest 400TC Cotton Sateen Sheet Set, Queen", "sheet-sets",
                    "Cloudrest", HOME_SELLER, "Hearth & Home Co.", "69.99", "89.99", "4.3", 980, 48,
                    "Four-piece 100% long-staple cotton sateen set with 16-inch deep pockets.",
                    Map.of("Size", "Queen", "Material", "100% cotton sateen", "Thread count", "400"));
            product(7114, "TL-TRM-RAIN-M-NV", "Trailmark Men's Waterproof Rain Jacket", "mens-jackets", "Trailmark",
                    APPAREL_SELLER, "Thread & Loom Apparel", "89.50", null, "4.4", 506, 22,
                    "Seam-sealed 2.5-layer shell with a packable hood and pit zips.",
                    Map.of("Size", "M", "Color", "Navy", "Waterproof rating", "10,000 mm"));
            product(7115, "TL-STR-LITE-W8", "Stride Lite Women's Running Shoes", "running-shoes", "Stride",
                    APPAREL_SELLER, "Thread & Loom Apparel", "94.00", "110.00", "4.5", 1388, 30,
                    "Breathable knit upper, responsive foam midsole and an 8 mm drop for daily training.",
                    Map.of("Size", "US 8", "Color", "Coral / White", "Drop", "8 mm"));
            product(7116, "TL-ORG-TEE-L-WHT", "Thread & Loom Men's Organic Cotton Crew T-Shirt", "mens-tshirts",
                    "Thread & Loom", APPAREL_SELLER, "Thread & Loom Apparel", "24.00", null, "4.2", 742, 150,
                    "Midweight 180 gsm organic cotton tee with a relaxed fit.",
                    Map.of("Size", "L", "Color", "White", "Material", "100% organic cotton"));
        }

        void product(long id, String sku, String title, String category, String brand, long sellerId,
                     String sellerName, String price, String listPrice, String rating, int ratingCount,
                     int available, String description, Map<String, String> attributes) {
            MockWorld.Product p = new MockWorld.Product();
            p.id = id;
            p.sku = sku;
            p.title = title;
            p.category = category;
            p.brand = brand;
            p.sellerId = sellerId;
            p.sellerName = sellerName;
            p.price = new BigDecimal(price);
            p.listPrice = listPrice == null ? null : new BigDecimal(listPrice);
            p.rating = new BigDecimal(rating);
            p.ratingCount = ratingCount;
            p.available = available;
            p.description = description;
            p.attributes.putAll(new LinkedHashMap<>(attributes));
            world.products.put(id, p);
        }

        // ---------------------------------------------------------------- orders

        /**
         * The happy world. recentDeliveryDays moves the "recent" delivery (5 in happy, 45 in ineligible);
         * splitShipment splits the shipped order in two; refundRecent fully refunds the recent delivery.
         */
        void happy(int recentDeliveryDays, boolean splitShipment, boolean refundRecent) {
            // 50001: PENDING, placed an hour ago, not paid yet
            MockWorld.Order pending = order(50001, customerId, OrderStatus.PENDING, ago(Duration.ofHours(1)),
                    line(7110, 1));
            pending.paidAmount = BigDecimal.ZERO;

            // 50002: SHIPPED three days ago
            MockWorld.Order shipped = order(50002, customerId, OrderStatus.SHIPPED, daysAgo(4),
                    line(7101, 1), line(7109, 1));
            if (splitShipment) {
                MockWorld.Shipment a = shipment(shipped, Carrier.FEDEX, "7749 3302 1185", ShipmentStatus.DELIVERED,
                        daysAgo(3), daysAgo(1), shipped.items.get(0));
                a.events.add(event(daysAgo(1), "Portland, OR", "Delivered, left at front door"));
                a.events.add(event(ago(Duration.ofHours(30)), "Portland, OR", "Out for delivery"));
                a.events.add(event(daysAgo(3), "Reno, NV", "Picked up by carrier"));
                MockWorld.Shipment b = shipment(shipped, Carrier.UPS, "1Z999AA10123456784", ShipmentStatus.IN_TRANSIT,
                        daysAgo(2), null, shipped.items.get(1));
                b.eta = LocalDate.ofInstant(now, ZoneOffset.UTC).plusDays(2);
                b.events.add(event(ago(Duration.ofHours(9)), "Sacramento, CA", "Departed UPS facility"));
                b.events.add(event(daysAgo(1), "Oakland, CA", "Arrived at UPS facility"));
                b.events.add(event(daysAgo(2), "San Jose, CA", "Picked up by carrier"));
            } else {
                MockWorld.Shipment s = shipment(shipped, Carrier.UPS, "1Z999AA10123456784", ShipmentStatus.IN_TRANSIT,
                        daysAgo(3), null, shipped.items.toArray(MockWorld.Item[]::new));
                s.eta = LocalDate.ofInstant(now, ZoneOffset.UTC).plusDays(1);
                s.events.add(event(ago(Duration.ofHours(6)), "Sacramento, CA", "Departed UPS facility"));
                s.events.add(event(daysAgo(1), "Oakland, CA", "Arrived at UPS facility"));
                s.events.add(event(daysAgo(2), "San Jose, CA", "In transit"));
                s.events.add(event(daysAgo(3), "San Jose, CA", "Picked up by carrier"));
            }

            // 50003: DELIVERED recently (5 days ago in happy); the skillet already has a return in progress
            MockWorld.Order recent = order(50003, customerId, OrderStatus.DELIVERED,
                    daysAgo(recentDeliveryDays + 4), line(7111, 1), line(7103, 1));
            delivered(recent, Carrier.USPS, "9400 1118 9922 3344 5566 77", recentDeliveryDays);
            if (refundRecent) {
                recent.status = OrderStatus.REFUNDED;
                refund(recent, null, recent.paidAmount, daysAgo(Math.max(recentDeliveryDays - 2, 1)),
                        "Customer goodwill: full refund");
            } else {
                returnOf(recent, recent.items.get(0), ReturnReason.DAMAGED, ReturnStatus.APPROVED,
                        daysAgo(Math.max(recentDeliveryDays - 2, 1)), null, "Handle cracked on arrival");
            }

            // 50004: DELIVERED 60 days ago, outside the return window
            MockWorld.Order old = order(50004, customerId, OrderStatus.DELIVERED, daysAgo(64),
                    line(7114, 1), line(7115, 1));
            delivered(old, Carrier.UPS, "1Z999AA10198765432", 60);

            // 50005: returned and refunded in full
            MockWorld.Order refunded = order(50005, customerId, OrderStatus.REFUNDED, daysAgo(40), line(7112, 1));
            delivered(refunded, Carrier.FEDEX, "7749 1200 5561", 36);
            MockWorld.Return r = returnOf(refunded, refunded.items.get(0), ReturnReason.DEFECTIVE,
                    ReturnStatus.CLOSED, daysAgo(33), daysAgo(26), "Stopped heating after a week");
            refund(refunded, r.id, refunded.paidAmount, daysAgo(26), "Return received: defective");

            // 50006: another customer's order, never visible to the caller as a customer
            MockWorld.Order foreign = order(50006, OTHER_CUSTOMER_ID, OrderStatus.DELIVERED, daysAgo(12),
                    line(7106, 1));
            delivered(foreign, Carrier.DHL, "JD014600003456789012", 9);

            ticket(true);
        }

        /** Two cancellable orders of the same Auralis headphones, so "cancel my headphones order" is ambiguous. */
        void ambiguousHeadphones() {
            order(50007, customerId, OrderStatus.PAID, daysAgo(1), line(7101, 1));
            MockWorld.Order pending = order(50008, customerId, OrderStatus.PENDING, ago(Duration.ofHours(2)),
                    line(7101, 1), line(7116, 2));
            pending.paidAmount = BigDecimal.ZERO;
        }

        record Line(long productId, int qty) {
        }

        Line line(long productId, int qty) {
            return new Line(productId, qty);
        }

        MockWorld.Order order(long id, long customer, OrderStatus status, Instant placedAt, Line... lines) {
            MockWorld.Order o = new MockWorld.Order();
            o.id = id;
            o.number = "AS-" + LocalDate.ofInstant(placedAt, ZoneOffset.UTC).getYear() + "-" + id;
            o.customerId = customer;
            o.status = status;
            o.placedAt = placedAt;
            for (Line l : lines) {
                MockWorld.Product p = world.products.get(l.productId());
                MockWorld.Item i = new MockWorld.Item();
                i.id = itemSeq++;
                i.orderId = id;
                i.productId = p.id;
                i.title = p.title;
                i.sku = p.sku;
                i.sellerId = p.sellerId;
                i.unitPrice = p.price;
                i.qty = l.qty();
                o.items.add(i);
            }
            BigDecimal subtotal = o.subtotal();
            o.shippingFee = subtotal.compareTo(FREE_SHIPPING_FROM) >= 0 ? BigDecimal.ZERO : new BigDecimal("5.99");
            o.tax = subtotal.multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP);
            o.paidAmount = status == OrderStatus.PENDING ? BigDecimal.ZERO : o.total();
            world.orders.put(id, o);
            return o;
        }

        MockWorld.Shipment shipment(MockWorld.Order o, Carrier carrier, String tracking, ShipmentStatus status,
                                    Instant shippedAt, Instant deliveredAt, MockWorld.Item... items) {
            MockWorld.Shipment s = new MockWorld.Shipment();
            s.id = shipmentSeq++;
            s.orderId = o.id;
            s.carrier = carrier;
            s.trackingNumber = tracking;
            s.status = status;
            s.shippedAt = shippedAt;
            s.deliveredAt = deliveredAt;
            for (MockWorld.Item i : items) i.shipmentId = s.id;
            world.shipments.put(s.id, s);
            return s;
        }

        void delivered(MockWorld.Order o, Carrier carrier, String tracking, int deliveredDaysAgo) {
            Instant delivered = daysAgo(deliveredDaysAgo);
            MockWorld.Shipment s = shipment(o, carrier, tracking, ShipmentStatus.DELIVERED,
                    daysAgo(deliveredDaysAgo + 3), delivered, o.items.toArray(MockWorld.Item[]::new));
            s.events.add(event(delivered, "Portland, OR", "Delivered"));
            s.events.add(event(delivered.minus(Duration.ofHours(5)), "Portland, OR", "Out for delivery"));
            s.events.add(event(daysAgo(deliveredDaysAgo + 3), "San Jose, CA", "Picked up by carrier"));
        }

        ShipmentView.Event event(Instant at, String location, String description) {
            return new ShipmentView.Event(at, location, description);
        }

        MockWorld.Return returnOf(MockWorld.Order o, MockWorld.Item item, ReturnReason reason, ReturnStatus status,
                                  Instant openedAt, Instant resolvedAt, String note) {
            MockWorld.Return r = new MockWorld.Return();
            r.id = returnSeq++;
            r.orderId = o.id;
            r.itemId = item.id;
            r.qty = item.qty;
            r.reason = reason;
            r.status = status;
            r.openedAt = openedAt;
            r.resolvedAt = resolvedAt;
            r.note = note;
            world.returns.put(r.id, r);
            return r;
        }

        void refund(MockWorld.Order o, Long returnId, BigDecimal amount, Instant at, String reason) {
            MockWorld.Refund f = new MockWorld.Refund();
            f.id = refundSeq++;
            f.orderId = o.id;
            f.returnId = returnId;
            f.amount = amount;
            f.status = RefundStatus.ISSUED;
            f.method = o.paymentMethod;
            f.createdAt = at;
            f.reason = reason;
            world.refunds.put(f.id, f);
        }

        // ---------------------------------------------------------------- tickets

        void ticket(boolean withHistory) {
            MockWorld.Ticket t = new MockWorld.Ticket();
            t.id = CALLER_TICKET_ID;
            t.customerId = customerId;
            t.subject = withHistory ? "Where is my headphones order?" : "Question about my account";
            t.status = TicketStatus.OPEN;
            t.tier = SupportTier.T1;
            if (withHistory) {
                message(t, AuthorType.CUSTOMER, "customer", "Hi, my order AS-50002 with the Auralis headphones "
                        + "says shipped but it has not arrived yet. Can you check?", false, daysAgo(1));
                message(t, AuthorType.AI, "assistant", "Your order shipped with UPS three days ago and is in "
                        + "transit; the carrier expects delivery tomorrow.", false, daysAgo(1).plusSeconds(20));
                message(t, AuthorType.STAFF, "support_t1_1", "Tracking looks normal, no carrier exception. "
                        + "Follow up if not delivered by ETA.", true, ago(Duration.ofHours(20)));
                message(t, AuthorType.CUSTOMER, "customer", "Thanks. Also, the skillet from my other order "
                        + "arrived with a cracked handle.", false, ago(Duration.ofHours(3)));
            } else {
                message(t, AuthorType.CUSTOMER, "customer", "How do I change my email address?", false,
                        ago(Duration.ofHours(2)));
            }
            world.tickets.put(t.id, t);

            MockWorld.Ticket other = new MockWorld.Ticket();
            other.id = OTHER_TICKET_ID;
            other.customerId = OTHER_CUSTOMER_ID;
            other.subject = "Monitor has a dead pixel";
            other.status = TicketStatus.PENDING;
            other.tier = SupportTier.T1;
            message(other, AuthorType.CUSTOMER, "customer", "There is a dead pixel near the center of my monitor.",
                    false, daysAgo(2));
            world.tickets.put(other.id, other);
        }

        void message(MockWorld.Ticket t, AuthorType type, String author, String body, boolean internal, Instant at) {
            MockWorld.Message m = new MockWorld.Message();
            m.id = messageSeq++;
            m.authorType = type;
            m.author = author;
            m.body = body;
            m.internal = internal;
            m.at = at;
            t.messages.add(m);
        }
    }

    /** All category slugs a product's category belongs to, itself first. */
    static List<String> categoryChain(String slug) {
        java.util.ArrayList<String> chain = new java.util.ArrayList<>();
        for (String s = slug; s != null; s = CATEGORY_PARENT.get(s)) chain.add(s);
        return chain;
    }
}
