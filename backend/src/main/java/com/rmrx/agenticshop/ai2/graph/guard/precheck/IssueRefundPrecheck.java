package com.rmrx.agenticshop.ai2.graph.guard.precheck;

import com.rmrx.agenticshop.ai2.tools.returns.RefundApprovalTools;
import com.rmrx.agenticshop.ai2.tools.returns.RefundTools;
import com.rmrx.agenticshop.app.returns.service.RefundCheck;
import com.rmrx.agenticshop.app.returns.service.RefundCommandService;
import org.springframework.stereotype.Component;

/**
 * {@code issue_refund}: within the caller's limit and what is left on the order. Above the limit the model is pointed
 * to {@code request_refund_approval} instead of an approval question that could only end in a refusal.
 */
@Component
class IssueRefundPrecheck implements SensitivePrecheck {

    private final RefundCommandService refunds;

    IssueRefundPrecheck(RefundCommandService refunds) {
        this.refunds = refunds;
    }

    @Override
    public String toolName() {
        return RefundTools.ISSUE_REFUND;
    }

    @Override
    public PrecheckResult check(PrecheckArgs args) {
        RefundCheck check = refunds.check(args.text("orderNumber"), args.decimal("amount"), args.id("returnId"));
        return switch (check.outcome()) {
            case ALLOWED -> PrecheckResult.allowed(RefundNotes.left(check));
            case NEEDS_APPROVAL -> PrecheckResult.blocked(check.amount() + " " + check.currency() + " is above the "
                    + "support agent's refund limit of " + check.cap() + " " + check.currency() + ". Do not retry "
                    + "issue_refund. Use " + RefundApprovalTools.REQUEST_REFUND_APPROVAL + " with the same order, "
                    + "amount and reason: a colleague with role " + check.approverRole() + " approves it, and only "
                    + "then is the money refunded. Tell the support agent that this refund needs approval.");
            case REJECTED -> PrecheckResult.blocked(check.reason());
        };
    }
}
