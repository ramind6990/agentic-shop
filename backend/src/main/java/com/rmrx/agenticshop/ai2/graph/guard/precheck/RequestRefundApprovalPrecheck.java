package com.rmrx.agenticshop.ai2.graph.guard.precheck;

import com.rmrx.agenticshop.ai2.tools.returns.RefundApprovalTools;
import com.rmrx.agenticshop.ai2.tools.returns.RefundTools;
import com.rmrx.agenticshop.app.returns.service.RefundApprovalService;
import com.rmrx.agenticshop.app.returns.service.RefundCheck;
import org.springframework.stereotype.Component;

/**
 * {@code request_refund_approval}: only for an amount above the caller's limit that is still possible on the order,
 * with no other request waiting for it.
 */
@Component
class RequestRefundApprovalPrecheck implements SensitivePrecheck {

    private final RefundApprovalService approvals;

    RequestRefundApprovalPrecheck(RefundApprovalService approvals) {
        this.approvals = approvals;
    }

    @Override
    public String toolName() {
        return RefundApprovalTools.REQUEST_REFUND_APPROVAL;
    }

    @Override
    public PrecheckResult check(PrecheckArgs args) {
        RefundCheck check = approvals.checkRequest(args.text("orderNumber"), args.decimal("amount"),
                args.id("returnId"));
        if (check.outcome() == RefundCheck.Outcome.NEEDS_APPROVAL) {
            return PrecheckResult.allowed("A colleague with role " + check.approverRole() + " decides it. "
                    + RefundNotes.left(check));
        }
        String hint = check.reason() != null && check.reason().contains("within your own limit")
                ? " Use " + RefundTools.ISSUE_REFUND + " instead." : "";
        return PrecheckResult.blocked(check.reason() + hint);
    }
}
