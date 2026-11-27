package com.rmrx.agenticshop.ai2.tools.returns;

import com.rmrx.agenticshop.ai2.contract.domain.RefundStatus;
import com.rmrx.agenticshop.ai2.contract.tool.response.returns.RefundListResponse;
import com.rmrx.agenticshop.ai2.contract.tool.response.returns.RefundResponse;
import com.rmrx.agenticshop.ai2.tools.AgentToolSet;
import com.rmrx.agenticshop.ai2.tools.Audience;
import com.rmrx.agenticshop.ai2.tools.Sensitive;
import com.rmrx.agenticshop.ai2.tools.ToolAudience;
import com.rmrx.agenticshop.ai2.tools.ToolCallExecutor;
import com.rmrx.agenticshop.ai2.tools.ToolDates;
import com.rmrx.agenticshop.ai2.tools.ToolLabel;
import com.rmrx.agenticshop.ai2.tools.ToolPaging;
import com.rmrx.agenticshop.app.common.dto.PageResponse;
import com.rmrx.agenticshop.app.returns.dto.ReturnDtos.RefundDto;
import com.rmrx.agenticshop.app.returns.service.RefundCommandService;
import com.rmrx.agenticshop.app.returns.service.RefundSearch;
import com.rmrx.agenticshop.app.returns.service.ReturnService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/** Refunds: finding them (customer variant: own; staff variant: any customer) and, for staff, issuing one. */
@Component
public class RefundTools implements AgentToolSet {

    public static final String SEARCH_REFUNDS = "search_refunds";
    public static final String ISSUE_REFUND = "issue_refund";

    private static final String SEARCH_DESCRIPTION = "Find refunds, newest first: a page of 10 with the amount (in "
            + "the order's currency), status, reason and the return it pays back, and the total.";
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final ReturnService returns;
    private final RefundCommandService commands;
    private final ReturnToolMapper mapper;

    public RefundTools(ReturnService returns, RefundCommandService commands, ReturnToolMapper mapper) {
        this.returns = returns;
        this.commands = commands;
        this.mapper = mapper;
    }

    @ToolAudience(Audience.CUSTOMER)
    @ToolLabel("Looking up your refunds")
    @Tool(name = SEARCH_REFUNDS, value = SEARCH_DESCRIPTION + " Only the customer's own refunds.")
    public RefundListResponse searchRefunds(
            @P(value = "Only refunds of this order", required = false) String orderNumber,
            @P(value = "Only refunds in these statuses", required = false) List<RefundStatus> statuses,
            @P(value = "Page 1 to 5; only when the user asks to see more", required = false) Integer page) {
        return search(new RefundSearch(orderNumber, null, mapper.toRefundStatuses(statuses), null, null, null, null),
                page);
    }

    @ToolAudience(Audience.STAFF)
    @ToolLabel("Looking up refunds")
    @Tool(name = SEARCH_REFUNDS, value = SEARCH_DESCRIPTION)
    public RefundListResponse searchRefundsForStaff(
            @P(value = "Only this customer's refunds; id from find_customers", required = false) Long customerId,
            @P(value = "Only refunds of this seller's orders", required = false) Long sellerId,
            @P(value = "Only refunds of this order", required = false) String orderNumber,
            @P(value = "Only refunds in these statuses", required = false) List<RefundStatus> statuses,
            @P(value = "Created on or after this date (yyyy-MM-dd)", required = false) String createdFrom,
            @P(value = "Created on or before this date (yyyy-MM-dd)", required = false) String createdTo,
            @P(value = "Page 1 to 5; only when the user asks to see more", required = false) Integer page) {
        return search(new RefundSearch(orderNumber, null, mapper.toRefundStatuses(statuses), customerId, sellerId,
                ToolDates.from(createdFrom, "createdFrom"), ToolDates.until(createdTo, "createdTo")), page);
    }

    @ToolAudience(Audience.STAFF)
    @ToolLabel("Issuing a refund for order {orderNumber}")
    @Sensitive(approvalMessage = "Refund {amount} on order {orderNumber}?",
            detail = "The money goes back to the customer's original payment method. Reason: {reason}")
    @Tool(name = ISSUE_REFUND, value = "Refund money on an order to the customer's original payment method, e.g. for "
            + "a received return or a lost parcel. The amount is limited by your role's refund limit and by what is "
            + "left to refund on the order. For an amount above your own limit use request_refund_approval instead: "
            + "a colleague with a higher limit approves it. The user approves it before it runs.")
    public RefundResponse issueRefund(
            @P("The order number") String orderNumber,
            @P("Amount to refund, in the order's currency") BigDecimal amount,
            @P("Why, in a short sentence") String reason,
            @P(value = "Id of the return this refund pays back, if any", required = false) Long returnId,
            InvocationParameters invocation) {
        String key = invocation.get(ToolCallExecutor.CALL_KEY);
        return new RefundResponse(mapper.toItem(commands.issue(orderNumber, amount, reason, returnId, key)));
    }

    private RefundListResponse search(RefundSearch search, Integer page) {
        PageResponse<RefundDto> found = returns.searchRefunds(search, ToolPaging.pageable(page, NEWEST_FIRST));
        return new RefundListResponse(found.content().stream().map(mapper::toItem).toList(), ToolPaging.info(found));
    }
}
