package com.rmrx.agenticshop.ai2.tools.order;

import com.rmrx.agenticshop.ai2.contract.domain.OrderStatus;
import com.rmrx.agenticshop.ai2.contract.tool.response.order.OrderCancellationResponse;
import com.rmrx.agenticshop.ai2.contract.tool.response.order.OrderDetailResponse;
import com.rmrx.agenticshop.ai2.contract.tool.response.order.OrderListResponse;
import com.rmrx.agenticshop.ai2.contract.tool.response.order.ShippingAddressChangeResponse;
import com.rmrx.agenticshop.ai2.tools.AgentToolSet;
import com.rmrx.agenticshop.ai2.tools.Audience;
import com.rmrx.agenticshop.ai2.tools.Sensitive;
import com.rmrx.agenticshop.ai2.tools.ToolAudience;
import com.rmrx.agenticshop.ai2.tools.ToolDates;
import com.rmrx.agenticshop.ai2.tools.ToolLabel;
import com.rmrx.agenticshop.ai2.tools.ToolPaging;
import com.rmrx.agenticshop.app.common.dto.PageResponse;
import com.rmrx.agenticshop.app.order.dto.OrderDtos.OrderOverview;
import com.rmrx.agenticshop.app.order.service.OrderCommandService;
import com.rmrx.agenticshop.app.order.service.OrderSearch;
import com.rmrx.agenticshop.app.order.service.OrderService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Orders: finding them, one order in full with what can be done with it, cancelling and changing the delivery
 * address. {@code search_orders} has a customer variant (own orders only) and a staff variant (any customer).
 */
@Component
@ToolAudience({Audience.CUSTOMER, Audience.STAFF})
public class OrderTools implements AgentToolSet {

    public static final String SEARCH_ORDERS = "search_orders";
    public static final String GET_ORDER = "get_order";
    public static final String CANCEL_ORDER = "cancel_order";
    public static final String UPDATE_SHIPPING_ADDRESS = "update_shipping_address";

    private static final String SEARCH_DESCRIPTION = "Find orders, newest first: a page of 10 with status, total, "
            + "item titles and the total number of matches. All filters are optional and combine.";
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("placedAt"), Sort.Order.desc("id"));

    private final OrderService orders;
    private final OrderCommandService commands;
    private final OrderToolMapper mapper;

    public OrderTools(OrderService orders, OrderCommandService commands, OrderToolMapper mapper) {
        this.orders = orders;
        this.commands = commands;
        this.mapper = mapper;
    }

    @ToolAudience(Audience.CUSTOMER)
    @ToolLabel("Looking up your orders")
    @Tool(name = SEARCH_ORDERS, value = SEARCH_DESCRIPTION + " Only the customer's own orders.")
    public OrderListResponse searchOrders(
            @P(value = "The order number or part of it, e.g. AS-261120-000123", required = false) String orderNumber,
            @P(value = "Only orders in these statuses", required = false) List<OrderStatus> statuses,
            @P(value = "Placed on or after this date (yyyy-MM-dd)", required = false) String placedFrom,
            @P(value = "Placed on or before this date (yyyy-MM-dd)", required = false) String placedTo,
            @P(value = "Words of a product title in the order, e.g. \"headphones\"", required = false)
            String productQuery,
            @P(value = "Page 1 to 5; only when the user asks to see more", required = false) Integer page) {
        return search(orderNumber, statuses, null, null, placedFrom, placedTo, productQuery, page);
    }

    @ToolAudience(Audience.STAFF)
    @ToolLabel("Looking up orders")
    @Tool(name = SEARCH_ORDERS, value = SEARCH_DESCRIPTION + " Narrow by customer (id from find_customers) whenever "
            + "you help a specific customer.")
    public OrderListResponse searchOrdersForStaff(
            @P(value = "Only this customer's orders; id from find_customers", required = false) Long customerId,
            @P(value = "Only orders of this seller", required = false) Long sellerId,
            @P(value = "The order number or part of it", required = false) String orderNumber,
            @P(value = "Only orders in these statuses", required = false) List<OrderStatus> statuses,
            @P(value = "Placed on or after this date (yyyy-MM-dd)", required = false) String placedFrom,
            @P(value = "Placed on or before this date (yyyy-MM-dd)", required = false) String placedTo,
            @P(value = "Words of a product title in the order", required = false) String productQuery,
            @P(value = "Page 1 to 5; only when the user asks to see more", required = false) Integer page) {
        return search(orderNumber, statuses, customerId, sellerId, placedFrom, placedTo, productQuery, page);
    }

    @ToolLabel("Looking up order {orderNumber}")
    @Tool(name = GET_ORDER, value = "One order in full: lines (with the itemId of each), delivery address, payments, "
            + "shipments, returns, refunds, and `actions`: whether it can be cancelled, sent to another address or "
            + "returned now (with the reason when not), the return deadline and the returnable quantity per itemId. "
            + "Check `actions` before any change to an order.")
    public OrderDetailResponse getOrder(@P("The order number, e.g. AS-261120-000123") String orderNumber) {
        return mapper.toDetail(orders.get(orderNumber), orders.actions(orderNumber));
    }

    @ToolLabel("Cancelling order {orderNumber}")
    @Sensitive(approvalMessage = "Cancel order {orderNumber}?",
            detail = "The whole order is cancelled. Anything already charged is refunded to the original payment "
                    + "method.")
    @Tool(name = CANCEL_ORDER, value = "Cancel a whole order that has not been shipped yet (PENDING or PAID). An "
            + "authorized payment is released; a charged one is refunded in full. The user approves it before it "
            + "runs.")
    public OrderCancellationResponse cancelOrder(
            @P("The order number") String orderNumber,
            @P(value = "Why the order is cancelled, in the customer's words", required = false) String reason) {
        return mapper.toCancellation(commands.cancel(orderNumber, reason));
    }

    @ToolLabel("Changing the delivery address of order {orderNumber}")
    @Sensitive(approvalMessage = "Send order {orderNumber} to the selected saved address?",
            detail = "The order is delivered to that address instead of the current one.")
    @Tool(name = UPDATE_SHIPPING_ADDRESS, value = "Send an order that has not been shipped yet (PENDING or PAID) to "
            + "another of the customer's saved addresses. The user approves it before it runs.")
    public ShippingAddressChangeResponse updateShippingAddress(
            @P("The order number") String orderNumber,
            @P("Id of one of the customer's saved addresses, from get_addresses") Long addressId) {
        return new ShippingAddressChangeResponse(orderNumber.trim().toUpperCase(),
                mapper.toAddress(commands.changeShippingAddress(orderNumber, addressId)));
    }

    private OrderListResponse search(String orderNumber, List<OrderStatus> statuses, Long customerId, Long sellerId,
                                     String placedFrom, String placedTo, String productQuery, Integer page) {
        OrderSearch search = new OrderSearch(orderNumber, mapper.toDomain(statuses), customerId, sellerId,
                ToolDates.from(placedFrom, "placedFrom"), ToolDates.until(placedTo, "placedTo"), productQuery);
        PageResponse<OrderOverview> found = orders.searchOverviews(search, ToolPaging.pageable(page, NEWEST_FIRST));
        return new OrderListResponse(found.content().stream().map(mapper::toCard).toList(), ToolPaging.info(found));
    }
}
