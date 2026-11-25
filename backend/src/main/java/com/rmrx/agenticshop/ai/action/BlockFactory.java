package com.rmrx.agenticshop.ai.action;

import com.rmrx.agenticshop.ai.action.ActionPolicy.Allowed;
import com.rmrx.agenticshop.ai.action.ActionTokenService.Issued;
import com.rmrx.agenticshop.ai.action.ActionTokenService.Spec;
import com.rmrx.agenticshop.ai.action.entity.ActionTargetType;
import com.rmrx.agenticshop.ai.context.AgentContext;
import com.rmrx.agenticshop.ai.contract.block.Block;
import com.rmrx.agenticshop.ai.contract.block.ChoiceRequestBlock;
import com.rmrx.agenticshop.ai.contract.block.EscalationBlock;
import com.rmrx.agenticshop.ai.contract.block.OrderDetailBlock;
import com.rmrx.agenticshop.ai.contract.block.OrderListBlock;
import com.rmrx.agenticshop.ai.contract.block.OrderSummaryItem;
import com.rmrx.agenticshop.ai.contract.block.PolicyAnswerBlock;
import com.rmrx.agenticshop.ai.contract.block.ProductDetailBlock;
import com.rmrx.agenticshop.ai.contract.block.ProductListBlock;
import com.rmrx.agenticshop.ai.contract.block.ProductSummaryItem;
import com.rmrx.agenticshop.ai.contract.block.RefundResultBlock;
import com.rmrx.agenticshop.ai.contract.block.ReturnStatusBlock;
import com.rmrx.agenticshop.ai.contract.block.ShipmentTrackingBlock;
import com.rmrx.agenticshop.ai.contract.response.ActionType;
import com.rmrx.agenticshop.ai.contract.response.Citation;
import com.rmrx.agenticshop.ai.contract.response.ItemAction;
import com.rmrx.agenticshop.ai.contract.tool.OrderDetailView;
import com.rmrx.agenticshop.ai.contract.tool.OrderSummaryView;
import com.rmrx.agenticshop.ai.contract.tool.PolicyChunkView;
import com.rmrx.agenticshop.ai.contract.tool.ProductDetailView;
import com.rmrx.agenticshop.ai.contract.tool.ProductSummaryView;
import com.rmrx.agenticshop.ai.contract.tool.RefundView;
import com.rmrx.agenticshop.ai.contract.tool.ReturnView;
import com.rmrx.agenticshop.ai.contract.tool.ShipmentView;
import com.rmrx.agenticshop.ai.tools.ToolData;
import com.rmrx.agenticshop.ai.tools.TurnResultRegistry;
import com.rmrx.agenticshop.ai.tools.port.OrderFacts;
import com.rmrx.agenticshop.ai.tools.port.OrderPort;
import com.rmrx.agenticshop.ai.tools.port.Page;
import com.rmrx.agenticshop.ai.tools.port.PortException;
import com.rmrx.agenticshop.order.entity.ShipmentStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Turns the tool results the reply shows into UI {@link Block}s with buttons. Block data comes only from the turn's
 * {@link TurnResultRegistry} (verified tool output), never from model text; the block id is the resultId (a result
 * that yields several blocks, e.g. two shipments, gets {@code r3}, {@code r3.2}, ...). Unknown resultIds are skipped
 * with a warning, never invented.
 *
 * <p>Buttons come from {@link ActionPolicy} applied to the orders' current state, which is read once for all orders of
 * the reply ({@link OrderPort#actionFacts}). Every button, including plain ones like VIEW_ORDER, gets a token stored
 * as an OPEN item action so a click can be validated later; all tokens of one reply are issued in one batch. Without a
 * ticket in the context no tokens can be stored, so blocks then carry no buttons.
 */
@Slf4j
@Component
public class BlockFactory {

    /**
     * The blocks of a reply, the reply-level suggestions (e.g. TALK_TO_HUMAN), the ids of all issued actions and the
     * envelope's citations (POLICY_ANSWER blocks point into it with citationIndexes).
     */
    public record BlockSet(List<Block> blocks, List<ItemAction> suggestions, List<Long> actionIds,
                           List<String> skipped, List<Citation> citations) {
    }

    private final OrderPort orders;
    private final ActionPolicy policy;
    private final ActionTokenService tokens;
    private final JsonMapper json;

    public BlockFactory(OrderPort orders, ActionPolicy policy, ActionTokenService tokens, JsonMapper json) {
        this.orders = orders;
        this.policy = policy;
        this.tokens = tokens;
        this.json = json;
    }

    /** Blocks for every registered result, in registration order. */
    public BlockSet buildAll(TurnResultRegistry registry, AgentContext ctx) {
        return build(registry, registry.all().stream().map(TurnResultRegistry.Entry::resultId).toList(), ctx);
    }

    public BlockSet build(TurnResultRegistry registry, List<String> resultIds, AgentContext ctx) {
        return build(registry, resultIds, ctx, true);
    }

    /**
     * replyActions=false leaves out the reply-level suggestions (TALK_TO_HUMAN), e.g. once the conversation was
     * handed to a human.
     */
    public BlockSet build(TurnResultRegistry registry, List<String> resultIds, AgentContext ctx,
                          boolean replyActions) {
        List<TurnResultRegistry.Entry> entries = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (String id : new LinkedHashSet<>(resultIds == null ? List.<String>of() : resultIds)) {
            var entry = registry.get(id);
            if (entry.isEmpty()) {
                log.warn("Block for unknown resultId '{}' skipped (request {})", id, ctx.requestId());
                skipped.add(id);
            } else {
                entries.add(entry.get());
            }
        }

        Map<Long, OrderFacts> facts = facts(ctx, orderIdsNeedingFacts(entries));
        Batch batch = new Batch(ctx);
        Citations citations = new Citations();
        Instant now = tokens.now();
        List<Supplier<List<Block>>> assemblers = new ArrayList<>();
        for (TurnResultRegistry.Entry e : entries) {
            Supplier<List<Block>> a = assembler(e, registry, facts, batch, citations, ctx, now);
            if (a == null) {
                log.warn("Result {} of tool {} has no block form; skipped", e.resultId(), e.toolName());
                skipped.add(e.resultId());
            } else {
                assemblers.add(a);
            }
        }
        List<Integer> suggestionRefs = !replyActions ? List.of() : policy.suggestions(ctx).stream()
                .map(al -> batch.add(al, null, null, null)).toList();

        batch.issue();
        List<Block> blocks = new ArrayList<>();
        assemblers.forEach(a -> blocks.addAll(a.get()));
        List<Long> actionIds = new ArrayList<>(batch.actionIds());
        entries.stream().filter(e -> e.data() instanceof ToolData.ConfirmationPending)
                .forEach(e -> actionIds.addAll(((ToolData.ConfirmationPending) e.data()).actionIds()));
        return new BlockSet(blocks, batch.items(suggestionRefs), actionIds, skipped, citations.list());
    }

    /** One order detail block with fresh buttons, e.g. the new state after a write. */
    public OrderDetailBlock orderDetail(String id, OrderDetailView view, AgentContext ctx) {
        Map<Long, OrderFacts> facts = facts(ctx, List.of(view.id()));
        Batch batch = new Batch(ctx);
        Supplier<OrderDetailBlock> a = orderDetailAssembler(id, view, facts.get(view.id()), batch, ctx, tokens.now());
        batch.issue();
        return a.get();
    }

    // ------------------------------------------------------------------ dispatch

    private Supplier<List<Block>> assembler(TurnResultRegistry.Entry e, TurnResultRegistry registry,
                                            Map<Long, OrderFacts> facts, Batch batch, Citations citations,
                                            AgentContext ctx, Instant now) {
        String id = e.resultId();
        return switch (e.data()) {
            case Page<?> page when isOrderPage(e, page) -> one(orderList(id, page, facts, batch, ctx));
            case Page<?> page when isProductPage(e, page) -> one(productList(id, page, batch, ctx));
            case OrderDetailView v -> one(orderDetailAssembler(id, v, facts.get(v.id()), batch, ctx, now));
            case ProductDetailView p -> one(productDetail(id, p, batch, ctx));
            case ToolData.Tracking t -> tracking(id, t, registry);
            case ToolData.ReturnStatus r -> returnStatus(id, r, registry, batch, ctx);
            case ToolData.ConfirmationPending c -> () -> List.of(c.withId(id));
            case ToolData.ChoicePending c -> one(choice(id, c, batch));
            case ToolData.PolicySearch p -> one(policyAnswer(id, p, citations));
            case ToolData.Escalated x -> () -> List.of(new EscalationBlock(id, x.reason(), x.ticketNumber(),
                    x.tier(), x.expectedResponse()));
            default -> null;
        };
    }

    private static <B extends Block> Supplier<List<Block>> one(Supplier<B> s) {
        return () -> List.of(s.get());
    }

    private static boolean isOrderPage(TurnResultRegistry.Entry e, Page<?> page) {
        return page.items().stream().anyMatch(OrderSummaryView.class::isInstance)
                || (page.items().isEmpty() && e.toolName().contains("orders"));
    }

    private static boolean isProductPage(TurnResultRegistry.Entry e, Page<?> page) {
        return page.items().stream().anyMatch(ProductSummaryView.class::isInstance)
                || (page.items().isEmpty() && e.toolName().contains("product"));
    }

    private static Set<Long> orderIdsNeedingFacts(List<TurnResultRegistry.Entry> entries) {
        Set<Long> ids = new LinkedHashSet<>();
        for (TurnResultRegistry.Entry e : entries) {
            if (e.data() instanceof Page<?> p) {
                p.items().stream().filter(OrderSummaryView.class::isInstance)
                        .forEach(o -> ids.add(((OrderSummaryView) o).id()));
            } else if (e.data() instanceof OrderDetailView v) {
                ids.add(v.id());
            }
        }
        return ids;
    }

    private Map<Long, OrderFacts> facts(AgentContext ctx, Collection<Long> orderIds) {
        if (orderIds.isEmpty() || ctx.ticketId() == null) return Map.of();
        try {
            return orders.actionFacts(ctx, orderIds);
        } catch (PortException e) {
            log.warn("Order facts unavailable ({}: {}); order buttons limited to VIEW_ORDER", e.code(),
                    e.getMessage());
            return Map.of();
        }
    }

    // ------------------------------------------------------------------ orders

    private Supplier<OrderListBlock> orderList(String id, Page<?> page, Map<Long, OrderFacts> facts, Batch batch,
                                               AgentContext ctx) {
        List<OrderSummaryView> views = page.items().stream().map(OrderSummaryView.class::cast).toList();
        List<List<Integer>> refs = views.stream().map(v -> orderButtons(v.id(), facts.get(v.id()), batch, ctx))
                .toList();
        return () -> {
            List<OrderSummaryItem> items = new ArrayList<>();
            for (int i = 0; i < views.size(); i++) {
                OrderSummaryView v = views.get(i);
                items.add(new OrderSummaryItem(v.id(), v.number(), v.status(), v.placedAt(), v.total(), v.currency(),
                        v.itemCount(), v.firstItem(), batch.items(refs.get(i))));
            }
            return new OrderListBlock(id, items, page.totalCount(), page.hasMore());
        };
    }

    private Supplier<OrderDetailBlock> orderDetailAssembler(String id, OrderDetailView v, OrderFacts f, Batch batch,
                                                            AgentContext ctx, Instant now) {
        List<Integer> orderRefs = orderButtons(v.id(), f, batch, ctx);
        List<List<Integer>> lineRefs = v.lines().stream().map(l -> {
            OrderFacts.ItemFacts item = f == null ? null : f.items().stream()
                    .filter(i -> Objects.equals(i.itemId(), l.itemId())).findFirst().orElse(null);
            if (item == null) return List.<Integer>of();
            return policy.forOrderLine(f, item, ctx, now).stream()
                    .map(a -> batch.add(a, ActionTargetType.ORDER_ITEM, l.itemId(), params("orderId", v.id())))
                    .toList();
        }).toList();
        return () -> {
            List<OrderDetailBlock.Line> lines = new ArrayList<>();
            for (int i = 0; i < v.lines().size(); i++) {
                OrderDetailView.Line l = v.lines().get(i);
                lines.add(new OrderDetailBlock.Line(l.itemId(), l.productId(), l.title(), l.sku(), l.unitPrice(),
                        l.qty(), l.lineTotal(), batch.items(lineRefs.get(i))));
            }
            Instant shippedAt = v.shipments().stream().map(ShipmentView::shippedAt).filter(Objects::nonNull)
                    .min(Comparator.naturalOrder()).orElse(null);
            boolean allDelivered = !v.shipments().isEmpty()
                    && v.shipments().stream().allMatch(s -> s.status() == ShipmentStatus.DELIVERED);
            Instant deliveredAt = !allDelivered ? null : v.shipments().stream().map(ShipmentView::deliveredAt)
                    .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
            return new OrderDetailBlock(id, v.id(), v.number(), v.status(), v.placedAt(), shippedAt, deliveredAt,
                    lines, v.subtotal(), v.shippingFee(), v.tax(), v.total(), v.currency(), batch.items(orderRefs));
        };
    }

    /** Order buttons from the policy; without facts only VIEW_ORDER, which needs no state. */
    private List<Integer> orderButtons(Long orderId, OrderFacts f, Batch batch, AgentContext ctx) {
        List<Allowed> allowed = f == null ? List.of(Allowed.of(ActionType.VIEW_ORDER)) : policy.forOrder(f, ctx);
        return allowed.stream().map(a -> batch.add(a, ActionTargetType.ORDER, orderId, null)).toList();
    }

    private Supplier<List<Block>> tracking(String id, ToolData.Tracking t, TurnResultRegistry registry) {
        String number = orderNumber(registry, t.orderId());
        return () -> {
            List<Block> out = new ArrayList<>();
            for (int i = 0; i < t.shipments().size(); i++) {
                ShipmentView s = t.shipments().get(i);
                out.add(new ShipmentTrackingBlock(subId(id, i), t.orderId(), number, s.carrier(), s.trackingNumber(),
                        s.status(), s.events().stream().map(ev -> new ShipmentTrackingBlock.TrackingEvent(ev.at(),
                        ev.location(), ev.description())).toList(), s.eta()));
            }
            return out;
        };
    }

    // ------------------------------------------------------------------ products

    private Supplier<ProductListBlock> productList(String id, Page<?> page, Batch batch, AgentContext ctx) {
        List<ProductSummaryView> views = page.items().stream().map(ProductSummaryView.class::cast).toList();
        List<List<Integer>> refs = views.stream().map(p -> productButtons(p.id(), batch, ctx)).toList();
        return () -> {
            List<ProductSummaryItem> items = new ArrayList<>();
            for (int i = 0; i < views.size(); i++) {
                ProductSummaryView p = views.get(i);
                items.add(new ProductSummaryItem(p.id(), p.sku(), p.title(), p.price(), null, p.currency(), p.rating(),
                        p.ratingCount(), null, p.inStock(), batch.items(refs.get(i))));
            }
            return new ProductListBlock(id, items, page.totalCount(), page.hasMore());
        };
    }

    private Supplier<ProductDetailBlock> productDetail(String id, ProductDetailView p, Batch batch,
                                                       AgentContext ctx) {
        List<Integer> refs = productButtons(p.id(), batch, ctx);
        return () -> new ProductDetailBlock(id, p.id(), p.sku(), p.title(), p.description(), p.price(), p.listPrice(),
                p.currency(), p.brand(), p.seller(), p.rating(), p.ratingCount(), p.available() > 0, p.restockEta(),
                null, p.attributes().entrySet().stream()
                .map(en -> new ProductDetailBlock.Attribute(en.getKey(), en.getValue())).toList(),
                batch.items(refs));
    }

    private List<Integer> productButtons(Long productId, Batch batch, AgentContext ctx) {
        return policy.forProduct(ctx).stream().map(a -> batch.add(a, ActionTargetType.PRODUCT, productId, null))
                .toList();
    }

    // ------------------------------------------------------------------ returns

    private Supplier<List<Block>> returnStatus(String id, ToolData.ReturnStatus r, TurnResultRegistry registry,
                                               Batch batch, AgentContext ctx) {
        List<List<Integer>> refs = r.returns().stream().map(ret -> policy.forReturn(ctx).stream()
                .map(a -> batch.add(a, ActionTargetType.ORDER, ret.orderId(), params("returnId", ret.id())))
                .toList()).toList();
        String fallbackNumber = orderNumber(registry, r.orderId());
        return () -> {
            List<Block> out = new ArrayList<>();
            int n = 0;
            for (int i = 0; i < r.returns().size(); i++) {
                ReturnView ret = r.returns().get(i);
                ReturnStatusBlock.RefundInfo refund = r.refunds().stream()
                        .filter(f -> Objects.equals(f.returnId(), ret.id())).findFirst()
                        .map(f -> new ReturnStatusBlock.RefundInfo(f.id(), f.amount(), f.currency(), f.status()))
                        .orElse(null);
                out.add(new ReturnStatusBlock(subId(id, n++), ret.id(), ret.orderId(),
                        ret.orderNumber() != null ? ret.orderNumber() : fallbackNumber, ret.product(), ret.qty(),
                        ret.status(), ret.reason(), ret.openedAt(), ret.resolvedAt(), refund,
                        batch.items(refs.get(i))));
            }
            for (RefundView f : r.refunds()) {
                if (f.returnId() != null) continue;
                out.add(new RefundResultBlock(subId(id, n++), f.id(), f.orderId(), f.amount(), f.currency(),
                        f.status(), f.method()));
            }
            return out;
        };
    }

    // ------------------------------------------------------------------ policies

    /**
     * A POLICY_ANSWER from a search_policies result: one point per retrieved section (heading, version, excerpt) and
     * the citations, built from the chunks. Citations are added to the envelope list in block order and deduplicated
     * by chunk id, so the first policy block's [1], [2] match the model's refs.
     */
    private static Supplier<PolicyAnswerBlock> policyAnswer(String id, ToolData.PolicySearch p, Citations citations) {
        List<Integer> indexes = new ArrayList<>();
        List<String> points = new ArrayList<>();
        for (PolicyChunkView c : p.chunks()) {
            indexes.add(citations.add(new Citation(c.documentId(), c.documentTitle(), c.documentType(),
                    c.versionId(), c.versionLabel(), c.sectionId(), c.sectionHeading(), c.effectiveFrom(),
                    c.effectiveTo(), c.chunkId(), snippet(c.excerpt(), SNIPPET_CHARS))));
            String heading = c.sectionHeading() == null ? c.documentTitle() : c.sectionHeading();
            points.add(heading + " (" + c.documentTitle() + " " + c.versionLabel() + "): "
                    + snippet(c.excerpt(), POINT_CHARS));
        }
        List<Integer> refs = List.copyOf(indexes);
        List<String> pts = List.copyOf(points);
        return () -> new PolicyAnswerBlock(id, p.query(), pts, refs);
    }

    static final int SNIPPET_CHARS = 240;
    static final int POINT_CHARS = 200;

    /** Plain text (markdown emphasis removed) cut at a word boundary. */
    static String snippet(String text, int max) {
        if (text == null) return "";
        String t = text.replace("**", "").replaceAll("\\s+", " ").strip();
        if (t.length() <= max) return t;
        int cut = t.lastIndexOf(' ', max - 1);
        if (cut < max / 2) cut = max - 1;
        return t.substring(0, cut).stripTrailing() + "…";
    }

    /** The envelope's citations of one reply, one per chunk. */
    private static final class Citations {

        private final List<Citation> list = new ArrayList<>();
        private final Map<Long, Integer> byChunk = new java.util.HashMap<>();

        int add(Citation c) {
            Integer existing = c.chunkId() == null ? null : byChunk.get(c.chunkId());
            if (existing != null) return existing;
            list.add(c);
            if (c.chunkId() != null) byChunk.put(c.chunkId(), list.size() - 1);
            return list.size() - 1;
        }

        List<Citation> list() {
            return List.copyOf(list);
        }
    }

    // ------------------------------------------------------------------ clarification

    /**
     * One SELECT_OPTION button per option, bound to the option's order; the options of one choice share a
     * {@code pending} group id, so using one marks the others used (see ActionService#claimPair), and carry
     * {@code then}, the action the click continues with.
     */
    private Supplier<ChoiceRequestBlock> choice(String id, ToolData.ChoicePending c, Batch batch) {
        String group = UUID.randomUUID().toString();
        List<Integer> refs = c.options().stream().map(o -> {
            ObjectNode p = params("orderId", o.orderId());
            p.put(ActionService.PENDING, group);
            p.put(ActionService.THEN, c.then().name());
            return batch.add(Allowed.of(ActionType.SELECT_OPTION), ActionTargetType.ORDER, o.orderId(), p);
        }).toList();
        return () -> {
            List<ChoiceRequestBlock.Option> options = new ArrayList<>();
            for (int i = 0; i < c.options().size(); i++) {
                ToolData.ChoiceOption o = c.options().get(i);
                List<ItemAction> a = batch.items(List.of(refs.get(i)));
                options.add(new ChoiceRequestBlock.Option(o.label(), o.description(), a.isEmpty() ? null : a.getFirst()));
            }
            return new ChoiceRequestBlock(id, c.prompt(), options);
        };
    }

    // ------------------------------------------------------------------ helpers

    /** The order number as some registered result reported it; null if none did. */
    private static String orderNumber(TurnResultRegistry registry, Long orderId) {
        for (TurnResultRegistry.Entry e : registry.all()) {
            switch (e.data()) {
                case OrderDetailView v when Objects.equals(v.id(), orderId) -> {
                    return v.number();
                }
                case Page<?> p -> {
                    for (Object o : p.items()) {
                        if (o instanceof OrderSummaryView s && Objects.equals(s.id(), orderId)) return s.number();
                    }
                }
                default -> {
                }
            }
        }
        return null;
    }

    private static String subId(String id, int index) {
        return index == 0 ? id : id + "." + (index + 1);
    }

    private ObjectNode params(String key, Long value) {
        ObjectNode node = json.createObjectNode();
        node.put(key, value);
        return node;
    }

    /** Collects the buttons of one reply, issues them in one batch, then hands them out by reference. */
    private final class Batch {

        private final AgentContext ctx;
        private final List<Spec> specs = new ArrayList<>();
        private List<Issued> issued = List.of();

        Batch(AgentContext ctx) {
            this.ctx = ctx;
        }

        int add(Allowed a, ActionTargetType targetType, Long targetId, ObjectNode params) {
            specs.add(new Spec(a.type(), a.label(), targetType, targetId, params, a.requiresConfirmation(),
                    tokens.itemTtl()));
            return specs.size() - 1;
        }

        void issue() {
            if (ctx.ticketId() == null) {
                if (!specs.isEmpty()) log.debug("No ticket in context; {} buttons not issued", specs.size());
                return;
            }
            issued = tokens.issueAll(ctx, specs);
        }

        List<ItemAction> items(List<Integer> refs) {
            if (issued.isEmpty()) return List.of();
            return refs.stream().map(i -> issued.get(i).toItemAction()).toList();
        }

        List<Long> actionIds() {
            return issued.stream().map(i -> i.action().getId()).toList();
        }
    }
}
