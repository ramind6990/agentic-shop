package com.rmrx.agenticshop.ai.tools.returns;

import com.rmrx.agenticshop.ai.contract.domain.ReturnDecision;
import com.rmrx.agenticshop.ai.contract.domain.ReturnReason;
import com.rmrx.agenticshop.ai.contract.domain.ReturnStatus;
import com.rmrx.agenticshop.ai.contract.tool.response.returns.ReturnListResponse;
import com.rmrx.agenticshop.ai.contract.tool.response.returns.ReturnResponse;
import com.rmrx.agenticshop.ai.tools.AgentToolSet;
import com.rmrx.agenticshop.ai.tools.Audience;
import com.rmrx.agenticshop.ai.tools.Sensitive;
import com.rmrx.agenticshop.ai.tools.ToolAudience;
import com.rmrx.agenticshop.ai.tools.ToolDates;
import com.rmrx.agenticshop.ai.tools.ToolLabel;
import com.rmrx.agenticshop.ai.tools.ToolPaging;
import com.rmrx.agenticshop.app.common.dto.PageResponse;
import com.rmrx.agenticshop.app.returns.dto.ReturnDtos.ReturnDto;
import com.rmrx.agenticshop.app.returns.service.ReturnCommandService;
import com.rmrx.agenticshop.app.returns.service.ReturnSearch;
import com.rmrx.agenticshop.app.returns.service.ReturnService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Return requests: finding them (customer variant: own; staff variant: any customer), opening and withdrawing one,
 * and, for staff, approving or rejecting one.
 */
@Component
@ToolAudience({Audience.CUSTOMER, Audience.STAFF})
public class ReturnTools implements AgentToolSet {

    public static final String SEARCH_RETURNS = "search_returns";
    public static final String REQUEST_RETURN = "request_return";
    public static final String CANCEL_RETURN = "cancel_return";
    public static final String DECIDE_RETURN = "decide_return";

    private static final String SEARCH_DESCRIPTION = "Find return requests, newest first: a page of 10 with the "
            + "product, quantity, reason and status, and the total. Refunds of returns are found with search_refunds.";
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("openedAt"), Sort.Order.desc("id"));

    private final ReturnService returns;
    private final ReturnCommandService commands;
    private final ReturnToolMapper mapper;

    public ReturnTools(ReturnService returns, ReturnCommandService commands, ReturnToolMapper mapper) {
        this.returns = returns;
        this.commands = commands;
        this.mapper = mapper;
    }

    @ToolAudience(Audience.CUSTOMER)
    @ToolLabel("Looking up your returns")
    @Tool(name = SEARCH_RETURNS, value = SEARCH_DESCRIPTION + " Only the customer's own returns.")
    public ReturnListResponse searchReturns(
            @P(value = "Only returns of this order", required = false) String orderNumber,
            @P(value = "Only returns in these statuses", required = false) List<ReturnStatus> statuses,
            @P(value = "Page 1 to 5; only when the user asks to see more", required = false) Integer page) {
        return search(new ReturnSearch(orderNumber, mapper.toReturnStatuses(statuses), null, null, null, null, null),
                page);
    }

    @ToolAudience(Audience.STAFF)
    @ToolLabel("Looking up returns")
    @Tool(name = SEARCH_RETURNS, value = SEARCH_DESCRIPTION)
    public ReturnListResponse searchReturnsForStaff(
            @P(value = "Only this customer's returns; id from find_customers", required = false) Long customerId,
            @P(value = "Only returns to this seller", required = false) Long sellerId,
            @P(value = "Only returns of this order", required = false) String orderNumber,
            @P(value = "Only returns in these statuses", required = false) List<ReturnStatus> statuses,
            @P(value = "Only returns with this reason", required = false) ReturnReason reason,
            @P(value = "Opened on or after this date (yyyy-MM-dd)", required = false) String openedFrom,
            @P(value = "Opened on or before this date (yyyy-MM-dd)", required = false) String openedTo,
            @P(value = "Page 1 to 5; only when the user asks to see more", required = false) Integer page) {
        return search(new ReturnSearch(orderNumber, mapper.toReturnStatuses(statuses), mapper.toDomain(reason),
                customerId, sellerId, ToolDates.from(openedFrom, "openedFrom"), ToolDates.until(openedTo, "openedTo")),
                page);
    }

    @ToolLabel("Opening a return for order {orderNumber}")
    @Sensitive(approvalMessage = "Return {quantity} unit(s) of an item from order {orderNumber} (reason: {reason})?",
            detail = "A return request is sent to the seller. The refund follows once the seller has received the "
                    + "item.")
    @Tool(name = REQUEST_RETURN, value = "Open a return for some units of one line of a delivered order, within the "
            + "return window. Take itemId and the returnable quantity from get_order (check actions.returnItems "
            + "first). The user approves it before it runs.")
    public ReturnResponse requestReturn(
            @P("The order number") String orderNumber,
            @P("itemId of the order line, from get_order") Long itemId,
            @P("How many units to return") int quantity,
            @P("Why the item is returned") ReturnReason reason,
            @P(value = "More detail in the customer's words", required = false) String note) {
        return new ReturnResponse(mapper.toItem(commands.request(orderNumber, itemId, quantity,
                mapper.toDomain(reason), note)));
    }

    @ToolLabel("Withdrawing return {returnId}")
    @Sensitive(approvalMessage = "Withdraw return request {returnId}?",
            detail = "The return is cancelled; the item stays with the customer.")
    @Tool(name = CANCEL_RETURN, value = "Withdraw a return request that is still REQUESTED (the seller has not acted "
            + "on it). The user approves it before it runs.")
    public ReturnResponse cancelReturn(@P("Return id from search_returns or get_order") Long returnId) {
        return new ReturnResponse(mapper.toItem(commands.cancel(returnId)));
    }

    @ToolAudience(Audience.STAFF)
    @ToolLabel("Deciding return {returnId}")
    @Sensitive(approvalMessage = "{decision} return request {returnId}?",
            detail = "The customer and the seller see the decision and the note.")
    @Tool(name = DECIDE_RETURN, value = "Approve or reject a return request that is REQUESTED or DISPUTED, with a "
            + "note explaining the decision. The user approves it before it runs.")
    public ReturnResponse decideReturn(
            @P("Return id") Long returnId,
            @P("APPROVE or REJECT") ReturnDecision decision,
            @P("Why, in a sentence the customer can read") String note) {
        return new ReturnResponse(mapper.toItem(commands.decide(returnId, decision == ReturnDecision.APPROVE, note)));
    }

    private ReturnListResponse search(ReturnSearch search, Integer page) {
        PageResponse<ReturnDto> found = returns.searchReturns(search, ToolPaging.pageable(page, NEWEST_FIRST));
        return new ReturnListResponse(found.content().stream().map(mapper::toItem).toList(), ToolPaging.info(found));
    }
}
