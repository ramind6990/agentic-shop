package com.rmrx.agenticshop.ai.tools;

import com.rmrx.agenticshop.ai.action.WriteGateway;
import com.rmrx.agenticshop.ai.action.WriteParams;
import com.rmrx.agenticshop.ai.context.AgentContext;
import com.rmrx.agenticshop.ai.contract.response.ActionType;
import com.rmrx.agenticshop.ai.tools.port.PortException;
import com.rmrx.agenticshop.app.returns.entity.ReturnReason;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;

/**
 * Propose tools for state-changing operations. They never change anything: each checks eligibility through
 * {@link WriteGateway#prepare} and, if eligible, puts a confirmation card with Confirm/Decline buttons in the reply.
 * Only the user's click on Confirm runs the write. Ineligible requests come back as a typed failure.
 */
public class WriteTools extends AbstractTools {

    public static final String REQUEST_ORDER_CANCELLATION = "request_order_cancellation";
    public static final String REQUEST_RETURN = "request_return";
    public static final String REQUEST_REFUND = "request_refund";

    private static final String CONFIRM_NOTE = " This does NOT perform the change: it shows the user a confirmation "
            + "card and nothing happens until the user confirms it themselves. Tell the user to review and confirm; "
            + "never say it is done.";

    private final WriteGateway gateway;

    WriteTools(AgentContext ctx, TurnResultRegistry registry, JsonMapper json, WriteGateway gateway) {
        super(ctx, registry, json);
        this.gateway = gateway;
    }

    @Tool(name = REQUEST_ORDER_CANCELLATION, value = "Proposes cancelling an order (only PENDING or PAID orders can "
            + "be cancelled; anything already paid is refunded)." + CONFIRM_NOTE)
    @ToolLabel("Preparing the cancellation…")
    public String requestOrderCancellation(@P("Numeric order id (not the order number)") Long orderId) {
        return run(REQUEST_ORDER_CANCELLATION, () -> {
            requireId("orderId", orderId);
            return propose(ActionType.CANCEL_ORDER, WriteParams.cancel(orderId));
        });
    }

    @Tool(name = REQUEST_RETURN, value = "Proposes a return for one delivered order line (within 30 days of "
            + "delivery, one open return per line). Get the orderItemId from get_order." + CONFIRM_NOTE)
    @ToolLabel("Preparing the return…")
    public String requestReturn(
            @P("Numeric order id") Long orderId,
            @P("Numeric order item id of the line to return, from get_order") Long orderItemId,
            @P("Why the customer returns it") ReturnReason reason,
            @P(value = "Short note in the customer's words, at most 500 characters", required = false) String note) {
        return run(REQUEST_RETURN, () -> {
            requireId("orderId", orderId);
            requireId("orderItemId", orderItemId);
            return propose(ActionType.START_RETURN, WriteParams.returnItem(orderId, orderItemId, reason, note));
        });
    }

    @Tool(name = REQUEST_REFUND, value = "Staff only: proposes refunding part or all of an order's paid amount to "
            + "the original payment method, within your role's refund limit (LIMIT_EXCEEDED above it: escalate)."
            + CONFIRM_NOTE)
    @ToolLabel("Preparing the refund…")
    public String requestRefund(
            @P("Numeric order id") Long orderId,
            @P("Amount in the order's currency, e.g. 40.00") Double amount,
            @P("Short reason recorded with the refund") String reason) {
        return run(REQUEST_REFUND, () -> {
            requireId("orderId", orderId);
            if (amount == null) throw PortException.invalid("amount is required");
            return propose(ActionType.REQUEST_REFUND,
                    WriteParams.refund(orderId, BigDecimal.valueOf(amount), reason));
        });
    }

    private ToolData.ConfirmationPending propose(ActionType op, WriteParams params) {
        if (ctx.ticketId() == null) throw PortException.invalid("no support ticket is bound to this conversation");
        return switch (gateway.prepare(ctx, op, params)) {
            case WriteGateway.Prepared.Ready r -> ToolData.ConfirmationPending.of(op, r.block(), r.actionIds());
            case WriteGateway.Prepared.Rejected r -> throw new PortException(r.code(), r.message());
        };
    }
}
