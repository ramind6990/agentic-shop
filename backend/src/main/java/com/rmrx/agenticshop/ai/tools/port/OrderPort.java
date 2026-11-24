package com.rmrx.agenticshop.ai.tools.port;

import com.rmrx.agenticshop.ai.context.AgentContext;
import com.rmrx.agenticshop.ai.contract.tool.OrderDetailView;
import com.rmrx.agenticshop.ai.contract.tool.OrderSummaryView;
import com.rmrx.agenticshop.ai.contract.tool.WriteOutcome;
import com.rmrx.agenticshop.order.entity.OrderStatus;

/**
 * Orders in the caller's scope: a customer sees their own, a seller orders containing their items (only their own
 * lines), staff all. Out-of-scope orders are NOT_FOUND. Failures are thrown as {@link PortException}.
 */
public interface OrderPort {

    /**
     * Newest first. customerId is ignored for customers (always themselves) and optional for others. page is
     * 1-based; size is capped at {@link Page#MAX_SIZE}.
     */
    Page<OrderSummaryView> listOrders(AgentContext ctx, Long customerId, OrderStatus status, int page, int size);

    OrderDetailView getOrder(AgentContext ctx, Long orderId);

    /** Allowed only from {@link WriteRules#CANCELLABLE}: CONFLICT if already cancelled, otherwise INELIGIBLE. */
    WriteOutcome<OrderDetailView> cancelOrder(AgentContext ctx, Long orderId);
}
