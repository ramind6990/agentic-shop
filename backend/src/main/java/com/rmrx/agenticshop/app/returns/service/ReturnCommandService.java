package com.rmrx.agenticshop.app.returns.service;

import com.rmrx.agenticshop.app.common.exception.BadRequestException;
import com.rmrx.agenticshop.app.common.exception.NotFoundException;
import com.rmrx.agenticshop.app.common.exception.RuleViolationException;
import com.rmrx.agenticshop.app.identity.repository.AppUserRepository;
import com.rmrx.agenticshop.app.order.entity.CustomerOrder;
import com.rmrx.agenticshop.app.order.entity.OrderItem;
import com.rmrx.agenticshop.app.order.service.OrderFacts;
import com.rmrx.agenticshop.app.order.service.OrderFactsLoader;
import com.rmrx.agenticshop.app.order.service.OrderLookup;
import com.rmrx.agenticshop.app.order.service.OrderRules;
import com.rmrx.agenticshop.app.returns.dto.ReturnDtos.ReturnDto;
import com.rmrx.agenticshop.app.returns.entity.ReturnReason;
import com.rmrx.agenticshop.app.returns.entity.ReturnRequest;
import com.rmrx.agenticshop.app.returns.entity.ReturnStatus;
import com.rmrx.agenticshop.app.returns.repository.ReturnRequestRepository;
import com.rmrx.agenticshop.app.security.AccessScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Set;

/** Opening, withdrawing and deciding return requests. */
@Service
@Transactional
public class ReturnCommandService {

    private static final Set<ReturnStatus> DECIDABLE = Set.of(ReturnStatus.REQUESTED, ReturnStatus.DISPUTED);

    private final OrderLookup orders;
    private final OrderFactsLoader facts;
    private final OrderRules rules;
    private final ReturnRequestRepository returns;
    private final AppUserRepository users;
    private final ReturnMapper mapper;
    private final AccessScope scope;
    private final Clock clock;

    public ReturnCommandService(OrderLookup orders, OrderFactsLoader facts, OrderRules rules,
                                ReturnRequestRepository returns, AppUserRepository users, ReturnMapper mapper,
                                AccessScope scope, Clock clock) {
        this.orders = orders;
        this.facts = facts;
        this.rules = rules;
        this.returns = returns;
        this.users = users;
        this.mapper = mapper;
        this.scope = scope;
        this.clock = clock;
    }

    /** Opens a return for some units of one line of a delivered order, within the seller's return window. */
    public ReturnDto request(String orderNumber, Long orderItemId, int quantity, ReturnReason reason, String note) {
        CustomerOrder order = orders.require(orderNumber);
        OrderFacts f = facts.load(order);
        OrderRules.require(rules.returnItems(f));
        OrderItem item = order.getItems().stream().filter(i -> i.getId().equals(orderItemId)).findFirst()
                .orElseThrow(() -> new NotFoundException("Item of order " + order.getOrderNumber(), orderItemId));
        int returnable = rules.returnableQuantity(f, item);
        if (quantity < 1) throw new BadRequestException("Quantity must be at least 1.");
        if (quantity > returnable) {
            throw new RuleViolationException("Only " + returnable + " unit(s) of this item can still be returned.");
        }
        return mapper.toReturn(returns.save(ReturnRequest.open(item, quantity, reason, note, clock.instant())));
    }

    /** Withdraws a return the seller has not acted on yet. */
    public ReturnDto cancel(Long returnId) {
        ReturnRequest r = requireVisible(returnId);
        if (r.getStatus() != ReturnStatus.REQUESTED) {
            throw new RuleViolationException("Only a REQUESTED return can be withdrawn; this one is "
                    + r.getStatus() + ".");
        }
        r.cancel(clock.instant());
        return mapper.toReturn(r);
    }

    /** Staff approve or reject a return that is requested or disputed. */
    public ReturnDto decide(Long returnId, boolean approve, String note) {
        scope.requireStaff("Return decision");
        ReturnRequest r = requireVisible(returnId);
        if (!DECIDABLE.contains(r.getStatus())) {
            throw new RuleViolationException("Only REQUESTED or DISPUTED returns can be decided; this one is "
                    + r.getStatus() + ".");
        }
        r.decide(approve, note, users.getReferenceById(scope.current().id()), clock.instant());
        return mapper.toReturn(r);
    }

    private ReturnRequest requireVisible(Long returnId) {
        ReturnRequest r = returns.findById(returnId).orElseThrow(() -> new NotFoundException("Return", returnId));
        scope.requireOwned(r.getCustomer().getId(), r.getOrder().getSeller().getId(), "Return", returnId);
        return r;
    }
}
