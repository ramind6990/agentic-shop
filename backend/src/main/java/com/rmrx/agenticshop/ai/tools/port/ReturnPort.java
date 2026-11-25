package com.rmrx.agenticshop.ai.tools.port;

import com.rmrx.agenticshop.ai.context.AgentContext;
import com.rmrx.agenticshop.ai.contract.tool.ReturnView;
import com.rmrx.agenticshop.ai.contract.tool.WriteOutcome;
import com.rmrx.agenticshop.app.returns.entity.ReturnReason;

import java.util.List;

/** Returns of orders in the caller's scope. */
public interface ReturnPort {

    ReturnView getReturn(AgentContext ctx, Long returnId);

    List<ReturnView> listReturnsForOrder(AgentContext ctx, Long orderId);

    /**
     * Opens a return for one delivered item within {@link WriteRules#RETURN_WINDOW_DAYS} days of delivery (else
     * INELIGIBLE); one open return per item (else CONFLICT). Sellers may not open returns (FORBIDDEN).
     */
    WriteOutcome<ReturnView> createReturn(AgentContext ctx, Long orderId, Long orderItemId, ReturnReason reason,
                                          String note);

    /**
     * Side-effect-free dry run of {@link #createReturn}: same checks and failures; on success amount is the line
     * total (the most the return can refund) and policyReason how the item meets the return window.
     */
    WritePreview checkReturn(AgentContext ctx, Long orderId, Long orderItemId, ReturnReason reason, String note);
}
