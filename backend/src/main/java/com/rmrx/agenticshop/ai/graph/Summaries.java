package com.rmrx.agenticshop.ai.graph;

import com.rmrx.agenticshop.ai.contract.block.Block;
import com.rmrx.agenticshop.ai.contract.block.ChoiceRequestBlock;
import com.rmrx.agenticshop.ai.contract.block.ConfirmationRequestBlock;
import com.rmrx.agenticshop.ai.contract.block.EscalationBlock;
import com.rmrx.agenticshop.ai.contract.block.OrderDetailBlock;
import com.rmrx.agenticshop.ai.contract.block.OrderListBlock;
import com.rmrx.agenticshop.ai.contract.block.PolicyAnswerBlock;
import com.rmrx.agenticshop.ai.contract.block.ProductDetailBlock;
import com.rmrx.agenticshop.ai.contract.block.ProductListBlock;
import com.rmrx.agenticshop.ai.contract.block.RefundResultBlock;
import com.rmrx.agenticshop.ai.contract.block.ReturnStatusBlock;
import com.rmrx.agenticshop.ai.contract.block.ShipmentTrackingBlock;
import com.rmrx.agenticshop.ai.contract.response.Citation;
import com.rmrx.agenticshop.ai.contract.response.ItemAction;
import com.rmrx.agenticshop.ai.contract.tool.OrderDetailView;
import com.rmrx.agenticshop.ai.contract.tool.OrderSummaryView;
import com.rmrx.agenticshop.ai.contract.tool.ProductDetailView;
import com.rmrx.agenticshop.ai.contract.tool.ProductSummaryView;
import com.rmrx.agenticshop.ai.contract.tool.RefundView;
import com.rmrx.agenticshop.ai.contract.tool.ReturnView;
import com.rmrx.agenticshop.ai.contract.tool.ShipmentView;
import com.rmrx.agenticshop.ai.tools.ToolData;
import com.rmrx.agenticshop.ai.tools.TurnResultRegistry;
import com.rmrx.agenticshop.ai.tools.port.Page;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Compact one-line descriptions of tool results and UI blocks, never full JSON. Used for the history of earlier
 * replies ({@code [shown r1 ORDER_LIST: 50001 AS-50001 PENDING Brewline…]}), the compose step's input and trace
 * summaries. Lists show at most {@link #MAX_ITEMS} items plus a "+N more" note.
 */
public final class Summaries {

    static final int MAX_ITEMS = 6;
    static final int MAX_LINE = 400;
    private static final int TITLE = 24;

    private Summaries() {
    }

    // ------------------------------------------------------------------ registry entries (raw tool data)

    /** {@code r1 get_my_orders → ORDER_LIST 3/12: 50001 AS-50001 PENDING Brewline…, …}. */
    public static String ofEntry(TurnResultRegistry.Entry e) {
        return cap(e.resultId() + " " + e.toolName() + " → " + ofData(e.data()));
    }

    static String ofData(Object data) {
        return switch (data) {
            case Page<?> p when p.items().stream().anyMatch(OrderSummaryView.class::isInstance) ->
                    "ORDER_LIST " + p.items().size() + "/" + p.totalCount() + ": "
                            + list(p.items(), o -> order((OrderSummaryView) o));
            case Page<?> p when p.items().stream().anyMatch(ProductSummaryView.class::isInstance) ->
                    "PRODUCT_LIST " + p.items().size() + "/" + p.totalCount() + ": "
                            + list(p.items(), o -> product((ProductSummaryView) o));
            case Page<?> p -> "EMPTY_LIST (0 results)";
            case OrderDetailView v -> "ORDER_DETAIL " + v.id() + " " + v.number() + " " + v.status() + " "
                    + money(v.total(), v.currency()) + ", lines: "
                    + list(v.lines(), l -> l.itemId() + " " + shorten(l.title()) + " x" + l.qty())
                    + ", shipments: " + v.shipments().size();
            case ProductDetailView p -> "PRODUCT_DETAIL " + p.id() + " " + shorten(p.title()) + " "
                    + money(p.price(), p.currency()) + (p.available() > 0 ? " in stock" : " out of stock");
            case ToolData.Tracking t -> "SHIPMENT_TRACKING order " + t.orderId() + ": "
                    + (t.shipments().isEmpty() ? "not shipped yet" : list(t.shipments(), Summaries::shipment));
            case ToolData.ReturnStatus r -> "RETURN_STATUS order " + r.orderId() + ": "
                    + r.totalReturns() + " returns " + list(r.returns(), Summaries::ret)
                    + "; " + r.totalRefunds() + " refunds " + list(r.refunds(), Summaries::refund);
            case ToolData.ConfirmationPending c -> "CONFIRMATION_PENDING " + c.operation() + " " + c.target()
                    + (c.amount() == null ? "" : " " + money(c.amount(), c.currency())) + " (awaiting user)";
            case ToolData.TicketHistory h -> "TICKET_HISTORY " + h.messageCount() + " messages";
            case ToolData.PolicySearch p -> "POLICY_SEARCH as of " + p.referenceDate() + ", " + p.chunkCount()
                    + " sections: " + list(p.chunks(), c -> "[" + c.ref() + "] " + source(c.documentTitle(),
                    c.versionLabel(), c.sectionHeading()));
            case ToolData.ChoicePending c -> "CHOICE_PENDING to " + c.then() + ": "
                    + list(c.options(), o -> o.orderId() + " " + o.label()) + " (awaiting user)";
            case ToolData.Escalated x -> "ESCALATED ticket " + x.ticketNumber() + " " + x.tier()
                    + (x.alreadyEscalated() ? " (was already escalated)" : "");
            case null -> "nothing";
            default -> data.getClass().getSimpleName();
        };
    }

    private static String order(OrderSummaryView o) {
        return o.id() + " " + o.number() + " " + o.status() + " " + shorten(o.firstItem());
    }

    private static String product(ProductSummaryView p) {
        return p.id() + " " + shorten(p.title()) + " " + money(p.price(), p.currency());
    }

    private static String shipment(ShipmentView s) {
        return s.carrier() + " " + s.status() + (s.eta() == null ? "" : " eta " + s.eta());
    }

    private static String ret(ReturnView r) {
        return r.id() + " " + r.status();
    }

    private static String refund(RefundView r) {
        return r.id() + " " + money(r.amount(), r.currency()) + " " + r.status();
    }

    // ------------------------------------------------------------------ blocks (as persisted in an envelope)

    /** {@code [shown r1 ORDER_LIST: …; r2 CONFIRMATION_REQUEST: …]}, or null when there are no blocks. */
    public static String ofBlocks(List<Block> blocks) {
        return ofBlocks(blocks, List.of());
    }

    /**
     * Like {@link #ofBlocks(List)}, with the envelope's citations so a POLICY_ANSWER names its sources (document,
     * version, section), never the policy text: {@code r1 POLICY_ANSWER: return window — Return & Refund Policy v3
     * (Return window)}.
     */
    public static String ofBlocks(List<Block> blocks, List<Citation> citations) {
        if (blocks == null || blocks.isEmpty()) return null;
        List<Citation> cites = citations == null ? List.of() : citations;
        return "[shown " + blocks.stream().map(b -> ofBlock(b, cites)).collect(Collectors.joining("; ")) + "]";
    }

    public static String ofBlock(Block b) {
        return ofBlock(b, List.of());
    }

    static String ofBlock(Block b, List<Citation> citations) {
        String body = switch (b) {
            case OrderListBlock o -> "ORDER_LIST: " + list(o.items(), i -> i.orderId() + " " + i.orderNumber()
                    + " " + i.status() + " " + shorten(i.firstItemTitle()));
            case OrderDetailBlock o -> "ORDER_DETAIL: " + o.orderId() + " " + o.orderNumber() + " " + o.status()
                    + " " + money(o.total(), o.currency()) + ", lines "
                    + list(o.lines(), l -> l.orderItemId() + " " + shorten(l.title()) + " x" + l.quantity());
            case ShipmentTrackingBlock s -> "SHIPMENT_TRACKING: order " + s.orderId() + " " + s.carrier() + " "
                    + s.status() + (s.estimatedDelivery() == null ? "" : " eta " + s.estimatedDelivery());
            case ProductListBlock p -> "PRODUCT_LIST: " + list(p.items(), i -> i.productId() + " "
                    + shorten(i.title()) + " " + money(i.price(), i.currency()));
            case ProductDetailBlock p -> "PRODUCT_DETAIL: " + p.productId() + " " + shorten(p.title()) + " "
                    + money(p.price(), p.currency());
            case ReturnStatusBlock r -> "RETURN_STATUS: return " + r.returnId() + " order " + r.orderNumber() + " "
                    + r.status();
            case RefundResultBlock r -> "REFUND_RESULT: refund " + r.refundId() + " order " + r.orderId() + " "
                    + money(r.amount(), r.currency()) + " " + r.status();
            case ConfirmationRequestBlock c -> "CONFIRMATION_REQUEST: " + c.title() + " — " + c.target()
                    + (c.amount() == null ? "" : " " + money(c.amount(), c.currency()))
                    + " (" + confirmationState(c.actions()) + ")";
            case PolicyAnswerBlock p -> "POLICY_ANSWER: " + shorten(p.question()) + policySources(p, citations);
            case ChoiceRequestBlock c -> "CHOICE_REQUEST: " + shorten(c.prompt()) + " "
                    + list(c.options(), o -> o.label() + (o.action() == null ? "" : " " + o.action().state()));
            case EscalationBlock e -> "ESCALATION: " + e.ticketNumber() + " " + e.tier();
        };
        return cap(b.id() + " " + body);
    }

    private static String policySources(PolicyAnswerBlock p, List<Citation> citations) {
        // grouped per document version: "Return & Refund Policy v3 (Return window, Refunds, +1 more)"
        java.util.Map<String, List<String>> byDoc = new java.util.LinkedHashMap<>();
        p.citationIndexes().stream()
                .filter(i -> i != null && i >= 0 && i < citations.size())
                .map(citations::get)
                .forEach(c -> {
                    List<String> sections = byDoc.computeIfAbsent(c.documentTitle() + " " + c.versionLabel(),
                            k -> new java.util.ArrayList<>());
                    if (c.sectionHeading() != null && !sections.contains(c.sectionHeading())) {
                        sections.add(c.sectionHeading());
                    }
                });
        if (byDoc.isEmpty()) return "";
        return " — " + byDoc.entrySet().stream()
                .map(e -> e.getKey() + (e.getValue().isEmpty() ? "" : " (" + sectionList(e.getValue()) + ")"))
                .collect(Collectors.joining("; "));
    }

    private static String sectionList(List<String> sections) {
        String head = String.join(", ", sections.subList(0, Math.min(2, sections.size())));
        return sections.size() > 2 ? head + ", +" + (sections.size() - 2) + " more" : head;
    }

    private static String source(String doc, String version, String section) {
        return doc + " " + version + (section == null ? "" : " (" + section + ")");
    }

    private static String confirmationState(List<ItemAction> actions) {
        if (actions == null || actions.isEmpty()) return "no buttons";
        return actions.stream().map(a -> a.type() + " " + a.state()).collect(Collectors.joining(", "));
    }

    // ------------------------------------------------------------------ helpers

    private static <T> String list(List<T> items, Function<T, String> f) {
        if (items == null || items.isEmpty()) return "none";
        String head = items.stream().limit(MAX_ITEMS).map(f).collect(Collectors.joining(", "));
        return items.size() > MAX_ITEMS ? head + ", +" + (items.size() - MAX_ITEMS) + " more" : head;
    }

    private static String money(BigDecimal amount, String currency) {
        return amount == null ? "" : amount.toPlainString() + (currency == null ? "" : " " + currency);
    }

    static String shorten(String s) {
        if (s == null) return "";
        return s.length() <= TITLE ? s : s.substring(0, TITLE - 1).stripTrailing() + "…";
    }

    static String cap(String s) {
        return s.length() <= MAX_LINE ? s : s.substring(0, MAX_LINE - 1) + "…";
    }
}
