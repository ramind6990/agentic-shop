package com.rmrx.agenticshop.app.order.service;

import com.rmrx.agenticshop.app.catalog.repository.InventoryRepository;
import com.rmrx.agenticshop.app.common.exception.NotFoundException;
import com.rmrx.agenticshop.app.identity.entity.Address;
import com.rmrx.agenticshop.app.identity.repository.AddressRepository;
import com.rmrx.agenticshop.app.order.dto.OrderDtos.AddressDto;
import com.rmrx.agenticshop.app.order.dto.OrderDtos.OrderCancellation;
import com.rmrx.agenticshop.app.order.dto.OrderDtos.Verdict;
import com.rmrx.agenticshop.app.order.entity.CustomerOrder;
import com.rmrx.agenticshop.app.order.entity.OrderItem;
import com.rmrx.agenticshop.app.order.entity.Payment;
import com.rmrx.agenticshop.app.order.entity.PaymentStatus;
import com.rmrx.agenticshop.app.order.entity.ShippingAddress;
import com.rmrx.agenticshop.app.order.repository.PaymentRepository;
import com.rmrx.agenticshop.app.returns.entity.Refund;
import com.rmrx.agenticshop.app.returns.repository.RefundRepository;
import com.rmrx.agenticshop.app.returns.service.RefundLedger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

/** Changes to an order made by its customer (or by support for them), checked by {@link OrderRules}. */
@Service
@Transactional
public class OrderCommandService {

    /** Days until a refund to the original payment method usually shows up. */
    private static final int REFUND_DAYS = 5;

    private final OrderLookup lookup;
    private final OrderFactsLoader facts;
    private final OrderRules rules;
    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final InventoryRepository inventory;
    private final AddressRepository addresses;
    private final OrderMapper mapper;
    private final RefundLedger ledger;
    private final Clock clock;

    public OrderCommandService(OrderLookup lookup, OrderFactsLoader facts, OrderRules rules,
                               PaymentRepository payments, RefundRepository refunds, InventoryRepository inventory,
                               AddressRepository addresses, OrderMapper mapper, RefundLedger ledger,
                               Clock clock) {
        this.lookup = lookup;
        this.facts = facts;
        this.rules = rules;
        this.payments = payments;
        this.refunds = refunds;
        this.inventory = inventory;
        this.addresses = addresses;
        this.mapper = mapper;
        this.ledger = ledger;
        this.clock = clock;
    }

    /**
     * Cancels an order that has not been shipped: an authorized payment is voided, a captured one is refunded in
     * full (minus anything refunded before), and reserved stock is released.
     */
    public OrderCancellation cancel(String orderNumber, String reason) {
        CustomerOrder order = lookup.require(orderNumber);
        OrderRules.require(rules.cancel(facts.load(order)));
        Instant now = clock.instant();
        order.cancel(reason, now);
        order.getItems().forEach(this::releaseStock);
        Payment paid = settlePayments(order);
        BigDecimal refunded = paid == null ? BigDecimal.ZERO : refundRest(order, reason, now);
        return new OrderCancellation(order.getOrderNumber(), order.getStatus(), now, refunded, order.getCurrency(),
                paid == null ? null : paid.getMethod(),
                paid == null ? null : LocalDate.ofInstant(now, ZoneOffset.UTC).plusDays(REFUND_DAYS));
    }

    /** Sends an order that has not been shipped to another saved address of its customer. */
    public AddressDto changeShippingAddress(String orderNumber, Long addressId) {
        CustomerOrder order = lookup.require(orderNumber);
        OrderRules.require(rules.changeAddress(facts.load(order)));
        Address saved = addresses.findById(addressId)
                .filter(a -> Objects.equals(a.getUser().getId(), order.getCustomer().getId()))
                .orElseThrow(() -> new NotFoundException("Saved address of the order's customer", addressId));
        order.shipTo(ShippingAddress.of(saved));
        return mapper.toAddress(order.getShippingAddress());
    }

    /** Whether {@link #cancel} would be allowed now, without changing anything. */
    @Transactional(readOnly = true)
    public Verdict checkCancel(String orderNumber) {
        return rules.cancel(facts.load(lookup.require(orderNumber)));
    }

    /** Whether {@link #changeShippingAddress} would be allowed now, without changing anything. */
    @Transactional(readOnly = true)
    public Verdict checkChangeAddress(String orderNumber, Long addressId) {
        CustomerOrder order = lookup.require(orderNumber);
        Verdict verdict = rules.changeAddress(facts.load(order));
        if (!verdict.allowed()) return verdict;
        boolean ownAddress = addressId != null && addresses.findById(addressId)
                .filter(a -> Objects.equals(a.getUser().getId(), order.getCustomer().getId()))
                .isPresent();
        return ownAddress ? verdict : Verdict.no("Address " + addressId + " is not a saved address of the order's "
                + "customer; use an id from get_addresses.");
    }

    private void releaseStock(OrderItem item) {
        inventory.findById(item.getProduct().getId()).ifPresent(stock -> stock.release(item.getQuantity()));
    }

    /** Voids authorizations; returns the charged payment to refund the rest of, if any. */
    private Payment settlePayments(CustomerOrder order) {
        Payment captured = null;
        List<Payment> all = payments.findByOrderIdOrderByCreatedAtAsc(order.getId());
        for (Payment p : all) {
            if (p.getStatus() == PaymentStatus.AUTHORIZED) p.voidAuthorization();
            if (p.getStatus() == PaymentStatus.CAPTURED || p.getStatus() == PaymentStatus.PARTIALLY_REFUNDED) {
                p.markRefunded(true);
                captured = p;
            }
        }
        return captured;
    }

    private BigDecimal refundRest(CustomerOrder order, String reason, Instant now) {
        BigDecimal rest = ledger.remaining(order);
        if (rest.signum() == 0) return rest;
        String why = "Order cancelled" + (reason == null || reason.isBlank() ? "" : ": " + reason);
        refunds.save(Refund.issue(order, null, rest, why, null, null, now));
        return rest;
    }
}
