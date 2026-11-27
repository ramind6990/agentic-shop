package com.rmrx.agenticshop.ai.tools.returns;

import com.rmrx.agenticshop.ai.contract.domain.RefundApprovalQueue;
import com.rmrx.agenticshop.ai.contract.domain.RefundApprovalStatus;
import com.rmrx.agenticshop.ai.contract.tool.response.returns.RefundApprovalListResponse;
import com.rmrx.agenticshop.ai.contract.tool.response.returns.RefundApprovalResponse;
import com.rmrx.agenticshop.ai.tools.AgentToolSet;
import com.rmrx.agenticshop.ai.tools.Audience;
import com.rmrx.agenticshop.ai.tools.Sensitive;
import com.rmrx.agenticshop.ai.tools.ToolAudience;
import com.rmrx.agenticshop.ai.tools.ToolCallExecutor;
import com.rmrx.agenticshop.ai.tools.ToolLabel;
import com.rmrx.agenticshop.ai.tools.ToolPaging;
import com.rmrx.agenticshop.app.common.dto.PageResponse;
import com.rmrx.agenticshop.app.returns.dto.ReturnDtos.RefundApprovalDto;
import com.rmrx.agenticshop.app.returns.service.RefundApprovalService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Four-eyes refunds for staff: asking a colleague to approve a refund above your own limit, finding requests, and
 * approving or rejecting a colleague's request.
 */
@Component
public class RefundApprovalTools implements AgentToolSet {

    public static final String REQUEST_REFUND_APPROVAL = "request_refund_approval";
    public static final String SEARCH_REFUND_APPROVALS = "search_refund_approvals";
    public static final String APPROVE_REFUND_REQUEST = "approve_refund_request";
    public static final String REJECT_REFUND_REQUEST = "reject_refund_request";

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("requestedAt"), Sort.Order.desc("id"));

    private final RefundApprovalService approvals;
    private final RefundApprovalToolMapper mapper;

    public RefundApprovalTools(RefundApprovalService approvals, RefundApprovalToolMapper mapper) {
        this.approvals = approvals;
        this.mapper = mapper;
    }

    @ToolAudience(Audience.STAFF)
    @ToolLabel("Asking for approval of a refund on order {orderNumber}")
    @Sensitive(approvalMessage = "Ask a colleague to approve a refund of {amount} on order {orderNumber}?",
            detail = "Nothing is paid now. A colleague whose refund limit covers the amount (T2 up to 1000, Finance "
                    + "above) approves or rejects it; the money goes back to the customer only once it is approved. "
                    + "Reason: {reason}")
    @Tool(name = REQUEST_REFUND_APPROVAL, value = "Ask a colleague to approve a refund that is ABOVE your own refund "
            + "limit (your limit is in \"The user\"). Use it instead of issue_refund when the amount is larger than you "
            + "may refund yourself, for example when issue_refund was refused because the amount is above your "
            + "limit. It creates a refund approval request (a number like RFA-0000012) for exactly this order, amount "
            + "and reason; NOTHING IS PAID NOW. A colleague whose limit covers the amount (SUPPORT_T2 up to 1000, "
            + "FINANCE above that) later approves it, which issues the refund, or rejects it; you can never decide "
            + "your own request. Refused when the amount is within your own limit (use issue_refund), when it is more "
            + "than what is left to refund on the order, or when the order already has a request waiting. Pass the "
            + "ticket number when the case has a support ticket: an internal note is added to it. The support agent "
            + "confirms before the request is sent. Afterwards tell them the request number and that the refund is "
            + "paid only after approval.")
    public RefundApprovalResponse requestRefundApproval(
            @P("The order number") String orderNumber,
            @P("Amount to refund, in the order's currency") BigDecimal amount,
            @P("Why the customer should get this money back, in a short sentence the approver can judge") String reason,
            @P(value = "Id of the return this refund pays back, if any", required = false) Long returnId,
            @P(value = "The support ticket of the case, if any, e.g. TCK-0015001", required = false)
            String ticketNumber,
            InvocationParameters invocation) {
        String key = invocation.get(ToolCallExecutor.CALL_KEY);
        return response(approvals.request(orderNumber, amount, reason, returnId, ticketNumber, key));
    }

    @ToolAudience(Audience.STAFF)
    @ToolLabel("Looking at refund approval requests")
    @Tool(name = SEARCH_REFUND_APPROVALS, value = "Refund approval requests, newest first: a page of 10 with the "
            + "request number, order, amount, reason, status, who asked and who decided, and the total. queue "
            + "TO_DECIDE (the default) lists waiting requests of colleagues that your limit covers: the ones you may "
            + "approve or reject. REQUESTED_BY_ME lists the requests you sent and their outcome. ALL lists every "
            + "request.")
    public RefundApprovalListResponse searchRefundApprovals(
            @P(value = "TO_DECIDE (default), REQUESTED_BY_ME or ALL", required = false) RefundApprovalQueue queue,
            @P(value = "Only requests in this status (not used with TO_DECIDE, which lists waiting ones)",
                    required = false) RefundApprovalStatus status,
            @P(value = "Page 1 to 5; only when the user asks to see more", required = false) Integer page) {
        PageResponse<RefundApprovalDto> found = approvals.search(mapper.toDomain(queue), mapper.toDomain(status),
                ToolPaging.pageable(page, NEWEST_FIRST));
        return new RefundApprovalListResponse(found.content().stream().map(mapper::toItem).toList(),
                ToolPaging.info(found));
    }

    @ToolAudience(Audience.STAFF)
    @ToolLabel("Approving refund request {approvalNumber}")
    @Sensitive(approvalMessage = "Approve refund request {approvalNumber}?",
            detail = "The refund is paid to the customer's original payment method right away, with you as the "
                    + "approver.")
    @Tool(name = APPROVE_REFUND_REQUEST, value = "Approve a colleague's waiting refund approval request: the refund is "
            + "issued at once, for the requested amount, with you as the approver. Only requests within your own "
            + "refund limit, never your own request. Look at the order first (get_order: what was paid, refunded and "
            + "returned) so the decision is informed. The support agent confirms before it runs.")
    public RefundApprovalResponse approveRefundRequest(
            @P("The request number, e.g. RFA-0000012") String approvalNumber,
            @P(value = "A short note for the requester, if any", required = false) String note,
            InvocationParameters invocation) {
        String key = invocation.get(ToolCallExecutor.CALL_KEY);
        return response(approvals.approve(approvalNumber, note, key));
    }

    @ToolAudience(Audience.STAFF)
    @ToolLabel("Rejecting refund request {approvalNumber}")
    @Sensitive(approvalMessage = "Reject refund request {approvalNumber}?",
            detail = "Nothing is refunded. Reason for the requester: {note}")
    @Tool(name = REJECT_REFUND_REQUEST, value = "Reject a colleague's waiting refund approval request: nothing is "
            + "refunded. Give the reason; the requester sees it. Only requests within your own refund limit, never "
            + "your own request. The support agent confirms before it runs.")
    public RefundApprovalResponse rejectRefundRequest(
            @P("The request number, e.g. RFA-0000012") String approvalNumber,
            @P("Why it is rejected, for the requester") String note) {
        return response(approvals.reject(approvalNumber, note));
    }

    private RefundApprovalResponse response(RefundApprovalDto approval) {
        return new RefundApprovalResponse(mapper.toItem(approval));
    }
}
