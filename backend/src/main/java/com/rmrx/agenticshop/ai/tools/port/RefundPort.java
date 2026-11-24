package com.rmrx.agenticshop.ai.tools.port;

import com.rmrx.agenticshop.ai.context.AgentContext;
import com.rmrx.agenticshop.ai.contract.tool.RefundView;
import com.rmrx.agenticshop.ai.contract.tool.WriteOutcome;

import java.math.BigDecimal;
import java.util.List;

/** Refunds of orders in the caller's scope. */
public interface RefundPort {

    List<RefundView> listRefunds(AgentContext ctx, Long orderId);

    /**
     * Issues a refund. Role limits per {@link WriteRules#checkRefundLimit} (FORBIDDEN / LIMIT_EXCEEDED); the amount
     * may not exceed the paid amount minus earlier refunds (INELIGIBLE).
     */
    WriteOutcome<RefundView> issueRefund(AgentContext ctx, Long orderId, BigDecimal amount, String reason);
}
