package com.rmrx.agenticshop.ai2.tools.support;

import com.rmrx.agenticshop.ai2.contract.domain.SupportTier;
import com.rmrx.agenticshop.ai2.contract.domain.TicketAssignee;
import com.rmrx.agenticshop.ai2.contract.domain.TicketCategory;
import com.rmrx.agenticshop.ai2.contract.domain.TicketPriority;
import com.rmrx.agenticshop.ai2.contract.domain.TicketStatus;
import com.rmrx.agenticshop.ai2.contract.tool.Texts;
import com.rmrx.agenticshop.ai2.contract.tool.response.support.TicketActionResponse;
import com.rmrx.agenticshop.ai2.contract.tool.response.support.TicketDetailResponse;
import com.rmrx.agenticshop.ai2.contract.tool.response.support.TicketListResponse;
import com.rmrx.agenticshop.ai2.tools.AgentToolSet;
import com.rmrx.agenticshop.ai2.tools.Audience;
import com.rmrx.agenticshop.ai2.tools.Sensitive;
import com.rmrx.agenticshop.ai2.tools.ToolAudience;
import com.rmrx.agenticshop.ai2.tools.ToolCallExecutor;
import com.rmrx.agenticshop.ai2.tools.ToolLabel;
import com.rmrx.agenticshop.ai2.tools.ToolPaging;
import com.rmrx.agenticshop.app.common.dto.PageResponse;
import com.rmrx.agenticshop.app.security.AccessScope;
import com.rmrx.agenticshop.app.support.dto.TicketDtos.TicketOpening;
import com.rmrx.agenticshop.app.support.dto.TicketDtos.TicketSummary;
import com.rmrx.agenticshop.app.support.service.TicketCommandService;
import com.rmrx.agenticshop.app.support.service.TicketSearch;
import com.rmrx.agenticshop.app.support.service.TicketService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Support tickets, the human channel: a customer's hand-off to support, and finding and reading tickets (customer
 * variant: own tickets; staff variant: the support queue). The desk's actions on a ticket are in
 * {@link TicketDeskTools}.
 */
@Component
public class TicketTools implements AgentToolSet {

    public static final String ESCALATE_TO_HUMAN = "escalate_to_human";
    public static final String SEARCH_TICKETS = "search_tickets";
    public static final String GET_TICKET = "get_ticket";

    static final int MESSAGES_SHOWN = 10;
    private static final Sort RECENT_FIRST = Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"));

    private final TicketService tickets;
    private final TicketCommandService commands;
    private final SupportToolMapper mapper;
    private final AccessScope scope;

    public TicketTools(TicketService tickets, TicketCommandService commands, SupportToolMapper mapper,
                       AccessScope scope) {
        this.tickets = tickets;
        this.commands = commands;
        this.mapper = mapper;
        this.scope = scope;
    }

    @ToolAudience(Audience.CUSTOMER)
    @ToolLabel("Passing your request to our support team")
    @Sensitive(approvalMessage = "Send your request to our support team?", detail = "{summary}")
    @Tool(name = ESCALATE_TO_HUMAN, value = "Hand the customer's request over to a human in the support team: opens a "
            + "support ticket with your summary (or adds it to the customer's open ticket about the same order). "
            + "Support answers in the ticket, usually within 24 hours. Use it when the customer asks for a person, or "
            + "needs something no tool can do. The customer approves the summary before it is sent.")
    public TicketActionResponse escalateToHuman(
            @P("What the ticket is about") TicketCategory category,
            @P("A short subject line, e.g. \"Parcel marked delivered but not received\"") String subject,
            @P("For the support team: what happened, what you already checked (with results), and what the "
                    + "customer wants. Plain sentences the customer can read.") String summary,
            @P(value = "The order it is about, if any", required = false) String orderNumber,
            InvocationParameters invocation) {
        Long conversationId = invocation.get(ToolCallExecutor.CONVERSATION_ID);
        TicketOpening opening = commands.open(orderNumber, mapper.toDomain(category), Texts.cut(subject, 240),
                Texts.cut(summary, 2000), conversationId);
        return new TicketActionResponse(mapper.toItem(opening.ticket()), opening.addedToOpenTicket());
    }

    @ToolAudience(Audience.CUSTOMER)
    @ToolLabel("Looking up your support tickets")
    @Tool(name = SEARCH_TICKETS, value = "The customer's support tickets, most recently updated first: a page of 10 "
            + "with status and the total.")
    public TicketListResponse searchTickets(
            @P(value = "Only tickets in these statuses", required = false) List<TicketStatus> statuses,
            @P(value = "Page 1 to 5; only when the user asks to see more", required = false) Integer page) {
        return search(new TicketSearch(null, mapper.toStatuses(statuses), null, null, null, null, null, false), page);
    }

    @ToolAudience(Audience.STAFF)
    @ToolLabel("Looking at the support queue")
    @Tool(name = SEARCH_TICKETS, value = "The support queue, most recently updated first: a page of 10 tickets and "
            + "the total. assignee ME = your tickets, UNASSIGNED = nobody has taken them yet.")
    public TicketListResponse searchTicketsForStaff(
            @P(value = "ME, UNASSIGNED or ANY (default)", required = false) TicketAssignee assignee,
            @P(value = "Only tickets in these statuses", required = false) List<TicketStatus> statuses,
            @P(value = "Only this tier", required = false) SupportTier tier,
            @P(value = "Only this priority", required = false) TicketPriority priority,
            @P(value = "Only this category", required = false) TicketCategory category,
            @P(value = "Only this customer's tickets; id from find_customers", required = false) Long customerId,
            @P(value = "Part of the subject or ticket number", required = false) String query,
            @P(value = "Page 1 to 5; only when the user asks to see more", required = false) Integer page) {
        Long assigneeId = assignee == TicketAssignee.ME ? scope.current().id() : null;
        return search(new TicketSearch(query, mapper.toStatuses(statuses), mapper.toDomain(priority),
                mapper.toDomain(category), mapper.toDomain(tier), customerId, assigneeId,
                assignee == TicketAssignee.UNASSIGNED), page);
    }

    @ToolAudience({Audience.CUSTOMER, Audience.STAFF})
    @ToolLabel("Reading ticket {ticketNumber}")
    @Tool(name = GET_TICKET, value = "One support ticket with its latest messages (oldest first): status, tier, "
            + "who works on it, and the conversation with support.")
    public TicketDetailResponse getTicket(@P("The ticket number, e.g. TCK-0015001") String ticketNumber) {
        var detail = tickets.get(ticketNumber);
        return new TicketDetailResponse(mapper.toItem(detail), detail.messageCount(),
                tickets.latestMessages(ticketNumber, MESSAGES_SHOWN).stream().map(mapper::toMessage).toList());
    }

    private TicketListResponse search(TicketSearch search, Integer page) {
        PageResponse<TicketSummary> found = tickets.search(search, ToolPaging.pageable(page, RECENT_FIRST));
        return new TicketListResponse(found.content().stream().map(mapper::toItem).toList(), ToolPaging.info(found));
    }
}
