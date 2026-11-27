package com.rmrx.agenticshop.ai.contract.tool.response.returns;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rmrx.agenticshop.ai.contract.domain.RefundApprovalStatus;
import com.rmrx.agenticshop.ai.contract.tool.FreeText;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * A request to approve a refund above the requester's own limit.
 *
 * @param approverRole the lowest role whose limit covers the amount (SUPPORT_T2 or FINANCE)
 * @param refundId     the refund issued when approved
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RefundApprovalItem(String approvalNumber, String orderNumber, BigDecimal amount, String currency,
                                 @FreeText String reason, RefundApprovalStatus status, String approverRole,
                                 String requestedBy, Instant requestedAt, String decidedBy, Instant decidedAt,
                                 @FreeText String decisionNote, Long refundId, Long returnId, String ticketNumber)
        implements Serializable {
}
