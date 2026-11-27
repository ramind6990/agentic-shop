package com.rmrx.agenticshop.ai.graph.guard.precheck;

import com.rmrx.agenticshop.app.returns.dto.ReturnDtos.RefundApprovalDto;
import com.rmrx.agenticshop.app.returns.service.ApprovalDecisionCheck;
import com.rmrx.agenticshop.app.returns.service.RefundApprovalService;

/**
 * Deciding a refund approval request (approve or reject): only a waiting request of a colleague, within the caller's
 * limit, and still possible on the order. The approval detail names what is decided.
 */
abstract class RefundDecisionPrecheck implements SensitivePrecheck {

    private final RefundApprovalService approvals;

    RefundDecisionPrecheck(RefundApprovalService approvals) {
        this.approvals = approvals;
    }

    @Override
    public PrecheckResult check(PrecheckArgs args) {
        ApprovalDecisionCheck check = approvals.checkDecision(args.text("approvalNumber"));
        if (!check.allowed()) return PrecheckResult.blocked(check.problem());
        RefundApprovalDto a = check.approval();
        return PrecheckResult.allowed(a.amount() + " " + a.currency() + " on order " + a.order().orderNumber()
                + ", requested by " + (a.requestedBy() == null ? "a colleague" : a.requestedBy().name())
                + ": " + a.reason());
    }
}
