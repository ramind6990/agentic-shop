package com.rmrx.agenticshop.ai2.tools.order;

import com.rmrx.agenticshop.ai2.contract.tool.response.OrderCancellationResponse;
import com.rmrx.agenticshop.ai2.contract.tool.response.OrderItem;
import com.rmrx.agenticshop.ai2.contract.tool.response.OrderLine;
import com.rmrx.agenticshop.ai2.contract.tool.response.OrderListResponse;
import com.rmrx.agenticshop.ai2.contract.tool.response.OrderStatus;
import com.rmrx.agenticshop.ai2.tools.AgentToolSet;
import com.rmrx.agenticshop.ai2.tools.Sensitive;
import com.rmrx.agenticshop.ai2.tools.ToolLabel;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Order tools of the agent.
 */
@Component
public class OrderTools implements AgentToolSet {

    public static final String SEARCH_ORDERS = "search_orders";
    public static final String CANCEL_ORDER = "cancel_order";

    private static final Set<OrderStatus> CANCELLABLE = Set.of(OrderStatus.PLACED, OrderStatus.PAID);

    private static final List<OrderItem> MOCK_ORDERS = List.of(
            new OrderItem("WE45", LocalDate.of(2026, 11, 10), OrderStatus.PLACED, new BigDecimal("164.40"), "EUR",
                    List.of(new OrderLine(101L, "Trail Runner Red", 1, new BigDecimal("89.90"),
                                    "https://cdn.example.com/products/101/thumb.jpg"),
                            new OrderLine(102L, "City Runner Blue", 1, new BigDecimal("74.50"),
                                    "https://cdn.example.com/products/102/thumb.jpg")),
                    "Berlin", "DHL-00349921", LocalDate.of(2026, 11, 28)),
            new OrderItem("WE51", LocalDate.of(2026, 11, 22), OrderStatus.PLACED, new BigDecimal("99.00"), "EUR",
                    List.of(new OrderLine(103L, "Night Runner Black", 1, new BigDecimal("99.00"),
                            "https://cdn.example.com/products/103/thumb.jpg")),
                    "Berlin", null, LocalDate.of(2026, 12, 2)),
            new OrderItem("WE32", LocalDate.of(2026, 9, 3), OrderStatus.DELIVERED, new BigDecimal("39.80"), "EUR",
                    List.of(new OrderLine(201L, "Running Socks (3 pairs)", 2, new BigDecimal("19.90"),
                            "https://cdn.example.com/products/201/thumb.jpg")),
                    "Berlin", "DHL-00211876", null),
            new OrderItem("WE18", LocalDate.of(2026, 6, 14), OrderStatus.CANCELLED, new BigDecimal("59.00"), "EUR",
                    List.of(new OrderLine(305L, "Trail Cap", 1, new BigDecimal("59.00"),
                            "https://cdn.example.com/products/305/thumb.jpg")),
                    "Berlin", null, null));

    @ToolLabel("Looking up your orders")
    @Tool(name = SEARCH_ORDERS, value = "Find the customer's own orders, newest first. Returns a page of orders with "
            + "their status, total and estimated delivery, the number of matching orders and whether more exist.")
    public OrderListResponse searchOrders(
            @P(value = "Only orders in these statuses; leave empty for all orders", required = false)
            List<OrderStatus> statuses) {
        // TODO the run's customer's orders from the order service (never trust an id from the model for ownership);
        //  canned happy response for now
        List<OrderItem> matching = MOCK_ORDERS.stream()
                .filter(o -> statuses == null || statuses.isEmpty() || statuses.contains(o.status()))
                .sorted(Comparator.comparing(OrderItem::placedAt).reversed())
                .toList();
        return new OrderListResponse(matching, matching.size(), false);
    }

    @ToolLabel("Cancelling order {orderNumber}")
    @Sensitive(approvalMessage = "Do you want to cancel order {orderNumber}?",
            detail = "The whole order is cancelled and its total is refunded to your original payment method.")
    @Tool(name = CANCEL_ORDER, value = "Cancel one of the customer's orders that has not been shipped yet (status "
            + "PLACED or PAID). The whole order is cancelled and refunded. The customer approves it before it runs.")
    public OrderCancellationResponse cancelOrder(
            @P("The order number, e.g. WE45") String orderNumber,
            @P(value = "Why the customer wants to cancel, in their words", required = false) String reason) {
        // TODO cancel through the order service, for the run's customer only; mock: nothing really changes
        OrderItem order = MOCK_ORDERS.stream()
                .filter(o -> o.orderNumber().equalsIgnoreCase(orderNumber))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No order " + orderNumber + " found for this customer."));
        if (!CANCELLABLE.contains(order.status())) {
            throw new IllegalStateException("Order " + order.orderNumber() + " is " + order.status()
                    + " and can no longer be cancelled.");
        }
        return new OrderCancellationResponse(order.orderNumber(), OrderStatus.CANCELLED, Instant.now(),
                order.total(), order.currency(), "original payment method", LocalDate.now().plusDays(5));
    }
}
