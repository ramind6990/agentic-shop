package com.rmrx.agenticshop.ai.tools;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.rmrx.agenticshop.ai.contract.block.ConfirmationRequestBlock;
import com.rmrx.agenticshop.ai.contract.response.ActionType;
import com.rmrx.agenticshop.ai.contract.tool.RefundView;
import com.rmrx.agenticshop.ai.contract.tool.ReturnView;
import com.rmrx.agenticshop.ai.contract.tool.ShipmentView;
import com.rmrx.agenticshop.ai.contract.tool.TicketMessageView;

import java.math.BigDecimal;
import java.time.Instant;
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

    /**
     * A write proposed and waiting for the user: what the model sees is the summary; the confirmation block with its
     * CONFIRM/DECLINE tokens and the action ids stay server-side (not serialized) for the reply's blocks.
     */
    public record ConfirmationPending(String status, ActionType operation, String title, String target,
                                      BigDecimal amount, String currency, String policyReason, Instant expiresAt,
                                      @JsonIgnore ConfirmationRequestBlock block, @JsonIgnore List<Long> actionIds) {

        public static final String AWAITING = "AWAITING_USER_CONFIRMATION";

        public ConfirmationPending {
            actionIds = actionIds == null ? List.of() : List.copyOf(actionIds);
        }

        public static ConfirmationPending of(ActionType operation, ConfirmationRequestBlock block,
                                             List<Long> actionIds) {
            return new ConfirmationPending(AWAITING, operation, block.title(), block.target(), block.amount(),
                    block.currency(), block.policyReason(), block.expiresAt(), block, actionIds);
        }

        /** The block under the reply's resultId. */
        public ConfirmationRequestBlock withId(String id) {
            return new ConfirmationRequestBlock(id, block.title(), block.description(), block.target(),
                    block.amount(), block.currency(), block.policyReason(), block.expiresAt(), block.actions());
        }
    }

    /** The latest messages of the bound ticket, oldest first. */
    public record TicketHistory(Long ticketId, List<TicketMessageView> messages, int messageCount) {
    }
}
