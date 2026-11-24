package com.rmrx.agenticshop.ai.tools;

import com.rmrx.agenticshop.ai.contract.tool.RefundView;
import com.rmrx.agenticshop.ai.contract.tool.ReturnView;
import com.rmrx.agenticshop.ai.contract.tool.ShipmentView;
import com.rmrx.agenticshop.ai.contract.tool.TicketMessageView;

import java.util.List;

/** Data shapes of tools that combine or wrap port results; lists are capped with a count and hasMore. */
public final class ToolData {

    private ToolData() {
    }

    /** Shipments of one order; an order not shipped yet has none. */
    public record Tracking(Long orderId, List<ShipmentView> shipments, int shipmentCount) {
    }

    /** Returns and refunds of one order (or the order of the requested return). */
    public record ReturnStatus(Long orderId, List<ReturnView> returns, int totalReturns, List<RefundView> refunds,
                               int totalRefunds, boolean hasMore) {
    }

    /** The latest messages of the bound ticket, oldest first. */
    public record TicketHistory(Long ticketId, List<TicketMessageView> messages, int messageCount) {
    }
}
