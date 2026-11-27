package com.rmrx.agenticshop.app.returns.service;

import com.rmrx.agenticshop.app.common.dto.PageResponse;
import com.rmrx.agenticshop.app.common.exception.NotFoundException;
import com.rmrx.agenticshop.app.common.exception.RuleViolationException;
import com.rmrx.agenticshop.app.identity.repository.AppUserRepository;
import com.rmrx.agenticshop.app.order.entity.CustomerOrder;
import com.rmrx.agenticshop.app.order.service.OrderLookup;
import com.rmrx.agenticshop.app.returns.dto.ReturnDtos.RefundApprovalDto;
import com.rmrx.agenticshop.app.returns.dto.ReturnDtos.RefundDto;
import com.rmrx.agenticshop.app.returns.entity.RefundApproval;
import com.rmrx.agenticshop.app.returns.entity.RefundApprovalStatus;
import com.rmrx.agenticshop.app.returns.entity.ReturnRequest;
import com.rmrx.agenticshop.app.returns.repository.RefundApprovalRepository;
import com.rmrx.agenticshop.app.returns.repository.RefundRepository;
import com.rmrx.agenticshop.app.returns.repository.ReturnRequestRepository;
import com.rmrx.agenticshop.app.security.AccessScope;
import com.rmrx.agenticshop.app.security.CurrentUser;
import com.rmrx.agenticshop.app.support.entity.Ticket;
import com.rmrx.agenticshop.app.support.service.TicketCommandService;
import com.rmrx.agenticshop.app.support.service.TicketLookup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Four-eyes refunds: a staff member asks for a refund above their own limit; a colleague whose limit covers the amount
 * approves it (the refund is issued then, with the approver as issuer) or rejects it. The requester never decides
 * their own request. When the case has a ticket, each step leaves an internal note on it.
 */
@Service
@Transactional
public class RefundApprovalService {

    private final RefundApprovalRepository approvals;
    private final RefundRepository refunds;
    private final ReturnRequestRepository returns;
    private final OrderLookup orders;
    private final TicketLookup tickets;
    private final TicketCommandService ticketCommands;
    private final AppUserRepository users;
    private final RefundRules rules;
    private final RefundCommandService refundCommands;
    private final ReturnMapper mapper;
    private final AccessScope scope;
    private final Clock clock;

    public RefundApprovalService(RefundApprovalRepository approvals, RefundRepository refunds,
                                 ReturnRequestRepository returns, OrderLookup orders, TicketLookup tickets,
                                 TicketCommandService ticketCommands, AppUserRepository users, RefundRules rules,
                                 RefundCommandService refundCommands, ReturnMapper mapper, AccessScope scope,
                                 Clock clock) {
        this.approvals = approvals;
        this.refunds = refunds;
        this.returns = returns;
        this.orders = orders;
        this.tickets = tickets;
        this.ticketCommands = ticketCommands;
        this.users = users;
        this.rules = rules;
        this.refundCommands = refundCommands;
        this.mapper = mapper;
        this.scope = scope;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ requesting

    /**
     * Asks for approval of a refund above the caller's limit. Refused when the caller may issue it themself, when it
     * is not possible at all, or when the order already has a waiting request.
     */
    public RefundApprovalDto request(String orderNumber, BigDecimal amount, String reason, Long returnId,
                                     String ticketNumber, String idempotencyKey) {
        Optional<RefundApproval> earlier = idempotencyKey == null ? Optional.empty()
                : approvals.findByIdempotencyKey(idempotencyKey);
        if (earlier.isPresent()) return mapper.toApproval(earlier.get());
        CurrentUser staff = requireRefunder();
        CustomerOrder order = orders.require(orderNumber);
        RefundCheck check = requestable(staff, order, amount);
        if (check.outcome() != RefundCheck.Outcome.NEEDS_APPROVAL) throw new RuleViolationException(check.reason());
        Ticket ticket = ticketNumber == null || ticketNumber.isBlank() ? null : tickets.require(ticketNumber);
        Instant now = clock.instant();
        RefundApproval approval = approvals.saveAndFlush(RefundApproval.open(temporaryNumber(), order,
                linkedReturn(order, returnId), amount, reason, check.approverRole(),
                users.getReferenceById(staff.id()), ticket, idempotencyKey, now));
        approval.assignNumber("RFA-" + String.format("%07d", approval.getId()));
        noteOnTicket(approval, "Refund approval " + approval.getApprovalNumber() + " requested: " + amount + " "
                + order.getCurrency() + " (" + reason + "). Waiting for " + check.approverRole() + ".");
        return mapper.toApproval(approval);
    }

    /**
     * Whether the caller may ask for this refund's approval now. NEEDS_APPROVAL = yes; ALLOWED = no, they can issue it
     * themself; REJECTED = no, with the reason.
     */
    @Transactional(readOnly = true)
    public RefundCheck checkRequest(String orderNumber, BigDecimal amount, Long returnId) {
        CurrentUser staff = requireRefunder();
        CustomerOrder order = orders.require(orderNumber);
        linkedReturn(order, returnId);
        return requestable(staff, order, amount);
    }

    private RefundCheck requestable(CurrentUser staff, CustomerOrder order, BigDecimal amount) {
        RefundCheck check = rules.check(staff.role(), order, amount);
        if (check.outcome() == RefundCheck.Outcome.ALLOWED) {
            return RefundCheck.rejected(amount + " " + check.currency() + " is within your own limit: issue the "
                    + "refund directly; no approval is needed.", amount, check.remaining(), check.currency());
        }
        if (check.outcome() == RefundCheck.Outcome.REJECTED) return check;
        return approvals.findFirstByOrderIdAndStatus(order.getId(), RefundApprovalStatus.PENDING)
                .map(waiting -> RefundCheck.rejected("Refund approval " + waiting.getApprovalNumber() + " ("
                        + waiting.getAmount() + " " + check.currency() + ") for this order is already waiting for a "
                        + "decision.", amount, check.remaining(), check.currency()))
                .orElse(check);
    }

    // ------------------------------------------------------------------ deciding

    /** Whether the caller may decide the request now (waiting, not their own, within their limit, still possible). */
    @Transactional(readOnly = true)
    public ApprovalDecisionCheck checkDecision(String approvalNumber) {
        CurrentUser staff = requireRefunder();
        RefundApproval approval = require(approvalNumber);
        return new ApprovalDecisionCheck(decisionProblem(staff, approval).orElse(null), mapper.toApproval(approval));
    }

    /** Approves the request: the refund is issued now, with the caller as issuer. */
    public RefundApprovalDto approve(String approvalNumber, String note, String idempotencyKey) {
        CurrentUser staff = requireRefunder();
        RefundApproval approval = require(approvalNumber);
        if (alreadyApprovedWith(approval, idempotencyKey)) return mapper.toApproval(approval);
        decisionProblem(staff, approval).ifPresent(problem -> {
            throw new RuleViolationException(problem);
        });
        RefundDto issued = refundCommands.issue(approval.getOrder().getOrderNumber(), approval.getAmount(),
                approval.getReason(), approval.getReturnRequest() == null ? null : approval.getReturnRequest().getId(),
                idempotencyKey);
        approval.approve(refunds.getReferenceById(issued.id()), users.getReferenceById(staff.id()), clock.instant(),
                note);
        noteOnTicket(approval, "Refund approval " + approvalNumber + " approved; refund of " + approval.getAmount()
                + " " + approval.getOrder().getCurrency() + " issued." + (blank(note) ? "" : " Note: " + note));
        return mapper.toApproval(approval);
    }

    /** Rejects the request: nothing is refunded. */
    public RefundApprovalDto reject(String approvalNumber, String note) {
        CurrentUser staff = requireRefunder();
        RefundApproval approval = require(approvalNumber);
        if (approval.getStatus() == RefundApprovalStatus.REJECTED && approval.getDecidedBy() != null
                && approval.getDecidedBy().getId().equals(staff.id())) {
            return mapper.toApproval(approval);
        }
        decisionProblem(staff, approval).ifPresent(problem -> {
            throw new RuleViolationException(problem);
        });
        approval.reject(users.getReferenceById(staff.id()), clock.instant(), note);
        noteOnTicket(approval, "Refund approval " + approvalNumber + " rejected." + (blank(note) ? "" : " Reason: "
                + note));
        return mapper.toApproval(approval);
    }

    private Optional<String> decisionProblem(CurrentUser staff, RefundApproval approval) {
        String number = approval.getApprovalNumber();
        if (!approval.isPending()) return Optional.of(number + " is already " + approval.getStatus() + ".");
        if (approval.getRequestedBy().getId().equals(staff.id())) {
            return Optional.of(number + " is your own request; a colleague has to decide it.");
        }
        if (!RefundLimits.covers(staff.role(), approval.getAmount())) {
            return Optional.of(number + " is for " + approval.getAmount() + " " + approval.getOrder().getCurrency()
                    + ", above your limit; it needs a colleague with role " + approval.getApproverRole() + ".");
        }
        RefundCheck still = rules.check(staff.role(), approval.getOrder(), approval.getAmount());
        if (still.outcome() == RefundCheck.Outcome.REJECTED) return Optional.of(still.reason());
        return Optional.empty();
    }

    private static boolean alreadyApprovedWith(RefundApproval approval, String idempotencyKey) {
        return idempotencyKey != null && approval.getRefund() != null
                && idempotencyKey.equals(approval.getRefund().getIdempotencyKey());
    }

    // ------------------------------------------------------------------ reading

    @Transactional(readOnly = true)
    public PageResponse<RefundApprovalDto> search(RefundApprovalQueue queue, RefundApprovalStatus status,
                                                  Pageable pageable) {
        CurrentUser staff = requireRefunder();
        return PageResponse.of(find(staff, queue == null ? RefundApprovalQueue.TO_DECIDE : queue, status, pageable),
                mapper::toApproval);
    }

    @Transactional(readOnly = true)
    public RefundApprovalDto get(String approvalNumber) {
        requireRefunder();
        return mapper.toApproval(require(approvalNumber));
    }

    private Page<RefundApproval> find(CurrentUser staff, RefundApprovalQueue queue, RefundApprovalStatus status,
                                      Pageable pageable) {
        return switch (queue) {
            case TO_DECIDE -> RefundLimits.capFor(staff.role())
                    .map(cap -> approvals.findByStatusAndRequestedByIdNotAndAmountLessThanEqual(
                            RefundApprovalStatus.PENDING, staff.id(), cap, pageable))
                    .orElseGet(() -> approvals.findByStatusAndRequestedByIdNot(RefundApprovalStatus.PENDING,
                            staff.id(), pageable));
            case REQUESTED_BY_ME -> status == null ? approvals.findByRequestedById(staff.id(), pageable)
                    : approvals.findByRequestedByIdAndStatus(staff.id(), status, pageable);
            case ALL -> status == null ? approvals.findAll(pageable) : approvals.findByStatus(status, pageable);
        };
    }

    // ------------------------------------------------------------------ helpers

    private CurrentUser requireRefunder() {
        CurrentUser staff = scope.current();
        if (!RefundLimits.mayIssue(staff.role())) throw new NotFoundException("Refund approval", "staff only");
        return staff;
    }

    private RefundApproval require(String approvalNumber) {
        String number = approvalNumber == null ? "" : approvalNumber.trim().toUpperCase();
        return approvals.findByApprovalNumber(number)
                .orElseThrow(() -> new NotFoundException("Refund approval", approvalNumber));
    }

    private ReturnRequest linkedReturn(CustomerOrder order, Long returnId) {
        if (returnId == null) return null;
        return returns.findById(returnId)
                .filter(r -> r.getOrder().getId().equals(order.getId()))
                .orElseThrow(() -> new NotFoundException("Return of order " + order.getOrderNumber(), returnId));
    }

    private void noteOnTicket(RefundApproval approval, String note) {
        Ticket ticket = approval.getTicket();
        if (ticket != null && ticket.isOpen()) ticketCommands.note(ticket.getTicketNumber(), note);
    }

    private static String temporaryNumber() {
        return "RFA-T" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }

    private static boolean blank(String text) {
        return text == null || text.isBlank();
    }
}
