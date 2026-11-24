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

import java.util.List;

/** Read tools over orders and shipments. */
public class OrderTools extends AbstractTools {

    public static final String GET_MY_ORDERS = "get_my_orders";
    public static final String FIND_CUSTOMER_ORDERS = "find_customer_orders";
    public static final String GET_ORDER = "get_order";
    public static final String TRACK_SHIPMENT = "track_shipment";

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
}
