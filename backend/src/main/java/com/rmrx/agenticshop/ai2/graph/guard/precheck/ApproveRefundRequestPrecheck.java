package com.rmrx.agenticshop.ai2.graph.guard.precheck;

import com.rmrx.agenticshop.ai2.tools.returns.RefundApprovalTools;
import com.rmrx.agenticshop.app.returns.service.RefundApprovalService;
import org.springframework.stereotype.Component;

@Component
class ApproveRefundRequestPrecheck extends RefundDecisionPrecheck {

    ApproveRefundRequestPrecheck(RefundApprovalService approvals) {
        super(approvals);
    }

    @Override
    public String toolName() {
        return RefundApprovalTools.APPROVE_REFUND_REQUEST;
    }
}
