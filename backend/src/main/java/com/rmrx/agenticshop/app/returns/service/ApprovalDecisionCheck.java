package com.rmrx.agenticshop.app.returns.service;

import com.rmrx.agenticshop.app.returns.dto.ReturnDtos.RefundApprovalDto;

/**
 * Whether the caller may decide a refund approval request now.
 *
 * @param problem why not; null when they may
 */
public record ApprovalDecisionCheck(String problem, RefundApprovalDto approval) {

    public boolean allowed() {
        return problem == null;
    }
}
