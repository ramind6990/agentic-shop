package com.rmrx.agenticshop.ai.contract.tool.response.returns;

import com.rmrx.agenticshop.ai.contract.tool.ToolResponse;

import java.io.Serializable;
import java.util.List;

/** One refund approval request after an action on it (requested, approved, rejected). */
public record RefundApprovalResponse(RefundApprovalItem approval) implements ToolResponse {

    /** The request and, in plain words, what it means for the customer's money (the agent must not guess it). */
    public record Summary(RefundApprovalItem approval, String outcome) implements Serializable {
    }

    @Override
    public Summary toAgentSummary() {
        return new Summary(approval, outcome());
    }

    @Override
    public List<String> itemIdentifiers() {
        return List.of(approval.approvalNumber());
    }

    private String outcome() {
        String amount = approval.amount() + " " + approval.currency();
        return switch (approval.status()) {
            case PENDING -> "Waiting for a colleague with role " + approval.approverRole() + ". Nothing has been "
                    + "refunded yet; the refund of " + amount + " is issued only if it is approved.";
            case APPROVED -> "Approved: a refund of " + amount + " was issued now to the customer's original payment "
                    + "method (refund id " + approval.refundId() + ").";
            case REJECTED -> "Rejected: nothing was refunded.";
        };
    }
}
