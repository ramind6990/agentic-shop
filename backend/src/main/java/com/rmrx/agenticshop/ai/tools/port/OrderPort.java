package com.rmrx.agenticshop.ai.tools.port;

import com.rmrx.agenticshop.ai.context.AgentContext;
import com.rmrx.agenticshop.ai.contract.tool.OrderDetailView;
import com.rmrx.agenticshop.ai.contract.tool.OrderSummaryView;
import com.rmrx.agenticshop.ai.contract.tool.WriteOutcome;
import com.rmrx.agenticshop.app.order.entity.OrderStatus;

import java.util.Collection;
import java.util.Map;

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

    /**
     * Side-effect-free dry run of {@link #cancelOrder}: the same checks against the current state, throwing the same
     * {@link PortException}s, and on success what the cancellation would do (amount = what would be refunded, null
     * when nothing was paid). Nothing changes.
     */
    WritePreview checkCancel(AgentContext ctx, Long orderId);

    /**
     * The state the action engine needs to decide which buttons each order gets, for many orders in one call. Orders
     * outside the caller's scope are left out; a seller only gets their own lines. Nothing changes.
     */
    Map<Long, OrderFacts> actionFacts(AgentContext ctx, Collection<Long> orderIds);
}
