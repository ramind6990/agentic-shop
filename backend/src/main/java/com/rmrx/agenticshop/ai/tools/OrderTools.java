package com.rmrx.agenticshop.ai.tools;

import com.rmrx.agenticshop.ai.context.AgentContext;
import com.rmrx.agenticshop.ai.contract.tool.ShipmentView;
import com.rmrx.agenticshop.ai.tools.port.OrderPort;
import com.rmrx.agenticshop.ai.tools.port.PortException;
import com.rmrx.agenticshop.ai.tools.port.ShipmentPort;
import com.rmrx.agenticshop.order.entity.OrderStatus;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import tools.jackson.databind.json.JsonMapper;

import com.rmrx.agenticshop.ai.contract.response.ActionType;
import com.rmrx.agenticshop.ai.contract.tool.OrderDetailView;
import com.rmrx.agenticshop.ai.contract.tool.OrderSummaryView;
import com.rmrx.agenticshop.ai.tools.port.Page;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Read tools over orders and shipments. */
public class OrderTools extends AbstractTools {

    public static final String GET_MY_ORDERS = "get_my_orders";
    public static final String FIND_CUSTOMER_ORDERS = "find_customer_orders";
    public static final String GET_ORDER = "get_order";
    public static final String TRACK_SHIPMENT = "track_shipment";
    public static final String ASK_USER_TO_CHOOSE_ORDER = "ask_user_to_choose_order";
    static final int MAX_CHOICES = 5;

    /** What the user's pick continues with; each maps to the order button of the same name. */
    public enum ChoicePurpose {
        VIEW_ORDER, TRACK_SHIPMENT, CANCEL_ORDER, START_RETURN, REQUEST_REFUND
    }

    private final OrderPort orders;
    private final ShipmentPort shipments;

    OrderTools(AgentContext ctx, TurnResultRegistry registry, JsonMapper json, OrderPort orders,
               ShipmentPort shipments) {
        super(ctx, registry, json);
        this.orders = orders;
        this.shipments = shipments;
    }

    @Tool(name = GET_MY_ORDERS, value = "Lists the signed-in customer's own orders, newest first, 10 per page, "
            + "with id, order number, status, date, total and first item. Use it to find an order the customer "
            + "talks about; then call get_order with its id for lines and shipments.")
    @ToolLabel("Looking up your orders\u2026")
    public String getMyOrders(
            @P(value = "Only orders in this status; omit for all", required = false) OrderStatus status,
            @P(value = "1-based page number; omit for the first page", required = false) Integer page) {
        return run(GET_MY_ORDERS, () -> {
            if (!ctx.isCustomer()) throw PortException.forbidden("get_my_orders is for customers");
            return orders.listOrders(ctx, null, status, page(page), PAGE_SIZE);
        });
    }

    @Tool(name = FIND_CUSTOMER_ORDERS, value = "Staff only: lists a customer's orders by customer id, newest "
            + "first, 10 per page.")
    @ToolLabel("Looking up the customer's orders\u2026")
    public String findCustomerOrders(
            @P("Numeric id of the customer whose orders to list") Long customerId,
            @P(value = "Only orders in this status; omit for all", required = false) OrderStatus status,
            @P(value = "1-based page number; omit for the first page", required = false) Integer page) {
        return run(FIND_CUSTOMER_ORDERS, () -> {
            requireId("customerId", customerId);
            return orders.listOrders(ctx, customerId, status, page(page), PAGE_SIZE);
        });
    }

    @Tool(name = GET_ORDER, value = "Gets one order with its lines (item ids, products, prices), totals and "
            + "shipments. Returns NOT_FOUND for orders the caller may not see.")
    @ToolLabel("Opening the order\u2026")
    public String getOrder(@P("Numeric order id (not the order number)") Long orderId) {
        return run(GET_ORDER, () -> {
            requireId("orderId", orderId);
            return orders.getOrder(ctx, orderId);
        });
    }

    @Tool(name = TRACK_SHIPMENT, value = "Tracks the shipments of an order: carrier, tracking number, status, "
            + "ETA and carrier events (newest first). An order can be split into several shipments; an order not "
            + "shipped yet has none.")
    @ToolLabel("Checking the shipment\u2026")
    public String trackShipment(@P("Numeric order id") Long orderId) {
        return run(TRACK_SHIPMENT, () -> {
            requireId("orderId", orderId);
            List<ShipmentView> list = shipments.track(ctx, orderId);
            return new ToolData.Tracking(orderId, list, list == null ? 0 : list.size());
        });
    }

    @Tool(name = ASK_USER_TO_CHOOSE_ORDER, value = "Asks the user to pick one of several orders when their request "
            + "could mean more than one (e.g. two orders with the same product). Use it instead of guessing. The "
            + "orders must have been looked up in this turn (get_my_orders, find_customer_orders or get_order); the "
            + "UI shows them as options with a button each, and the click continues with `purpose` for the chosen "
            + "order. After calling it, ask your clarifying question in one short sentence and stop.")
    @ToolLabel("Preparing the options…")
    public String askUserToChooseOrder(
            @P("Numeric ids of the 2 to 5 candidate orders, from this turn's results") List<Long> orderIds,
            @P("What to do with the chosen order") ChoicePurpose purpose) {
        return run(ASK_USER_TO_CHOOSE_ORDER, () -> {
            if (ctx.ticketId() == null) throw PortException.invalid("no support ticket is bound to this conversation");
            if (purpose == null) throw PortException.invalid("purpose is required");
            if (purpose == ChoicePurpose.REQUEST_REFUND
                    && !ToolCatalog.allowedTools(ctx.role()).contains(WriteTools.REQUEST_REFUND)) {
                throw PortException.forbidden("refunds are proposed by support staff only");
            }
            LinkedHashSet<Long> ids = new LinkedHashSet<>(orderIds == null ? List.of() : orderIds);
            ids.remove(null);
            if (ids.size() < 2 || ids.size() > MAX_CHOICES) {
                throw PortException.invalid("give 2 to " + MAX_CHOICES + " distinct order ids");
            }
            Map<Long, ToolData.ChoiceOption> known = knownOrders();
            List<ToolData.ChoiceOption> options = new ArrayList<>();
            for (Long id : ids) {
                ToolData.ChoiceOption o = known.get(id);
                if (o == null) {
                    throw PortException.invalid("order " + id + " was not looked up in this turn; list the orders "
                            + "first and pass ids from that result");
                }
                options.add(o);
            }
            ActionType then = ActionType.valueOf(purpose.name());
            return new ToolData.ChoicePending(ToolData.ChoicePending.AWAITING, then, prompt(then), options);
        });
    }

    /** Orders registered in this turn (lists and details), labelled from their data; the latest entry wins. */
    private Map<Long, ToolData.ChoiceOption> knownOrders() {
        Map<Long, ToolData.ChoiceOption> out = new LinkedHashMap<>();
        for (TurnResultRegistry.Entry e : registry().all()) {
            switch (e.data()) {
                case Page<?> p -> {
                    for (Object item : p.items()) {
                        if (item instanceof OrderSummaryView v) {
                            String what = v.firstItem() == null ? null
                                    : v.firstItem() + (v.itemCount() > 1 ? " +" + (v.itemCount() - 1) + " more" : "");
                            out.put(v.id(), option(v.id(), v.number(), v.status(), v.placedAt(), what, v.total(),
                                    v.currency()));
                        }
                    }
                }
                case OrderDetailView v -> {
                    String what = v.lines().isEmpty() ? null : v.lines().getFirst().title()
                            + (v.lines().size() > 1 ? " +" + (v.lines().size() - 1) + " more" : "");
                    out.put(v.id(), option(v.id(), v.number(), v.status(), v.placedAt(), what, v.total(),
                            v.currency()));
                }
                default -> {
                }
            }
        }
        return out;
    }

    private static ToolData.ChoiceOption option(Long id, String number, OrderStatus status, java.time.Instant placed,
                                                String what, java.math.BigDecimal total, String currency) {
        List<String> parts = new ArrayList<>();
        if (status != null) parts.add(status.name());
        if (placed != null) parts.add("placed " + placed.atZone(ZoneOffset.UTC).toLocalDate());
        if (what != null) parts.add(what);
        if (total != null) parts.add(total.toPlainString() + (currency == null ? "" : " " + currency));
        return new ToolData.ChoiceOption(id, "Order " + number, String.join(" · ", parts));
    }

    static String prompt(ActionType then) {
        return switch (then) {
            case CANCEL_ORDER -> "Which order do you want to cancel?";
            case TRACK_SHIPMENT -> "Which order do you want to track?";
            case START_RETURN -> "Which order do you want to return an item from?";
            case REQUEST_REFUND -> "Which order should the refund be for?";
            default -> "Which order do you mean?";
        };
    }
}
