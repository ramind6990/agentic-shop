package com.rmrx.agenticshop.app.returns.service;

import com.rmrx.agenticshop.app.common.exception.BadRequestException;
import com.rmrx.agenticshop.app.common.exception.NotFoundException;
import com.rmrx.agenticshop.app.common.exception.RuleViolationException;
import com.rmrx.agenticshop.app.identity.repository.AppUserRepository;
import com.rmrx.agenticshop.app.order.entity.CustomerOrder;
import com.rmrx.agenticshop.app.order.entity.Payment;
import com.rmrx.agenticshop.app.order.entity.PaymentStatus;
import com.rmrx.agenticshop.app.order.repository.PaymentRepository;
import com.rmrx.agenticshop.app.order.service.OrderLookup;
import com.rmrx.agenticshop.app.returns.dto.ReturnDtos.RefundDto;
import com.rmrx.agenticshop.app.returns.entity.Refund;
import com.rmrx.agenticshop.app.returns.entity.ReturnRequest;
import com.rmrx.agenticshop.app.returns.repository.RefundRepository;
import com.rmrx.agenticshop.app.returns.repository.ReturnRequestRepository;
import com.rmrx.agenticshop.app.security.AccessScope;
import com.rmrx.agenticshop.app.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Optional;
import java.util.Set;

/**
 * Refunds issued by staff, within their role's limit and what is left to refund on the order. A repeated request with
 * the same idempotency key returns the first refund instead of paying twice.
 */
@Service
@Transactional
public class RefundCommandService {

    private static final Set<PaymentStatus> REFUNDABLE = Set.of(PaymentStatus.CAPTURED,
            PaymentStatus.PARTIALLY_REFUNDED);

    private final OrderLookup orders;
    private final RefundRepository refunds;
    private final ReturnRequestRepository returns;
    private final PaymentRepository payments;
    private final AppUserRepository users;
    private final ReturnMapper mapper;
    private final AccessScope scope;
    private final RefundLedger ledger;
    private final Clock clock;

    public RefundCommandService(OrderLookup orders, RefundRepository refunds, ReturnRequestRepository returns,
                                PaymentRepository payments, AppUserRepository users, ReturnMapper mapper,
                                AccessScope scope, RefundLedger ledger, Clock clock) {
        this.orders = orders;
        this.refunds = refunds;
        this.returns = returns;
        this.payments = payments;
        this.users = users;
        this.mapper = mapper;
        this.scope = scope;
        this.ledger = ledger;
        this.clock = clock;
    }

    public RefundDto issue(String orderNumber, BigDecimal amount, String reason, Long returnId,
                           String idempotencyKey) {
        Optional<Refund> earlier = idempotencyKey == null ? Optional.empty()
                : refunds.findByIdempotencyKey(idempotencyKey);
        if (earlier.isPresent()) return mapper.toRefund(earlier.get());
        CurrentUser staff = scope.current();
        if (!RefundLimits.mayIssue(staff.role())) throw new NotFoundException("Refund action", "staff only");
        CustomerOrder order = orders.require(orderNumber);
        checkAmount(order, amount, staff);
        Refund refund = refunds.save(Refund.issue(order, linkedReturn(order, returnId), amount, reason,
                users.getReferenceById(staff.id()), idempotencyKey, clock.instant()));
        markRefunded(order, ledger.remaining(order).signum() == 0);
        return mapper.toRefund(refund);
    }

    private void checkAmount(CustomerOrder order, BigDecimal amount, CurrentUser staff) {
        if (amount == null || amount.signum() <= 0) throw new BadRequestException("The amount must be positive.");
        RefundLimits.capFor(staff.role()).filter(cap -> amount.compareTo(cap) > 0).ifPresent(cap -> {
            throw new RuleViolationException("Your role may refund at most " + cap + " " + order.getCurrency()
                    + "; a supervisor must issue larger refunds.");
        });
        BigDecimal left = ledger.remaining(order);
        if (amount.compareTo(left) > 0) {
            throw new RuleViolationException("Only " + left + " " + order.getCurrency()
                    + " of this order can still be refunded.");
        }
    }

    private ReturnRequest linkedReturn(CustomerOrder order, Long returnId) {
        if (returnId == null) return null;
        return returns.findById(returnId)
                .filter(r -> r.getOrder().getId().equals(order.getId()))
                .orElseThrow(() -> new NotFoundException("Return of order " + order.getOrderNumber(), returnId));
    }

    private void markRefunded(CustomerOrder order, boolean fully) {
        order.markRefunded(fully);
        for (Payment p : payments.findByOrderIdOrderByCreatedAtAsc(order.getId())) {
            if (REFUNDABLE.contains(p.getStatus())) p.markRefunded(fully);
        }
    }
}
