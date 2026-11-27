package com.rmrx.agenticshop.app.order.service;

import com.rmrx.agenticshop.app.common.dto.PageResponse;
import com.rmrx.agenticshop.app.common.dto.RefMapper;
import com.rmrx.agenticshop.app.common.repository.Specs;
import com.rmrx.agenticshop.app.order.dto.OrderDtos.OrderActions;
import com.rmrx.agenticshop.app.order.dto.OrderDtos.OrderDetail;
import com.rmrx.agenticshop.app.order.dto.OrderDtos.OrderOverview;
import com.rmrx.agenticshop.app.order.dto.OrderDtos.OrderSummary;
import com.rmrx.agenticshop.app.order.entity.CustomerOrder;
import com.rmrx.agenticshop.app.order.entity.OrderItem;
import com.rmrx.agenticshop.app.order.entity.OrderStatus;
import com.rmrx.agenticshop.app.order.entity.Shipment;
import com.rmrx.agenticshop.app.order.repository.CustomerOrderRepository;
import com.rmrx.agenticshop.app.order.repository.PaymentRepository;
import com.rmrx.agenticshop.app.order.repository.ShipmentRepository;
import com.rmrx.agenticshop.app.returns.repository.RefundRepository;
import com.rmrx.agenticshop.app.returns.repository.ReturnRequestRepository;
import com.rmrx.agenticshop.app.returns.service.ReturnMapper;
import com.rmrx.agenticshop.app.security.AccessScope;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class OrderService {

    private final CustomerOrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ShipmentRepository shipmentRepository;
    private final ReturnRequestRepository returnRepository;
    private final RefundRepository refundRepository;
    private final OrderMapper mapper;
    private final ReturnMapper returnMapper;
    private final RefMapper refs;
    private final AccessScope scope;
    private final OrderLookup lookup;
    private final OrderFactsLoader facts;
    private final OrderRules rules;

    public OrderService(CustomerOrderRepository orderRepository, PaymentRepository paymentRepository,
                        ShipmentRepository shipmentRepository, ReturnRequestRepository returnRepository,
                        RefundRepository refundRepository, OrderMapper mapper, ReturnMapper returnMapper,
                        RefMapper refs, AccessScope scope, OrderLookup lookup, OrderFactsLoader facts,
                        OrderRules rules) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.shipmentRepository = shipmentRepository;
        this.returnRepository = returnRepository;
        this.refundRepository = refundRepository;
        this.mapper = mapper;
        this.returnMapper = returnMapper;
        this.refs = refs;
        this.scope = scope;
        this.lookup = lookup;
        this.facts = facts;
        this.rules = rules;
    }

    public PageResponse<OrderSummary> search(String q, OrderStatus status, Long customerId, Long sellerId,
                                             Instant from, Instant to, Pageable pageable) {
        OrderSearch search = new OrderSearch(q, status == null ? List.of() : List.of(status), customerId, sellerId,
                from, to, null);
        return PageResponse.of(orderRepository.findAll(spec(search), pageable), mapper::toSummary);
    }

    /** A page of orders with their lines, delivery city and latest shipment (for order cards). */
    public PageResponse<OrderOverview> searchOverviews(OrderSearch search, Pageable pageable) {
        Page<CustomerOrder> page = orderRepository.findAll(spec(search), pageable);
        Map<Long, Shipment> latestShipment = shipmentRepository
                .findByOrderIdInOrderByIdAsc(page.map(CustomerOrder::getId).getContent()).stream()
                .collect(Collectors.toMap(s -> s.getOrder().getId(), Function.identity(), (a, b) -> b));
        return PageResponse.of(page, o -> new OrderOverview(mapper.toSummary(o), mapper.toAddress(o.getShippingAddress()),
                o.getItems().stream().map(mapper::toItem).toList(),
                mapper.toShipment(latestShipment.get(o.getId()))));
    }

    /** Full order view: lines, payments, shipments, returns and refunds. Accepts the id or the order number. */
    public OrderDetail get(String idOrNumber) {
        CustomerOrder o = lookup.require(idOrNumber);
        Long id = o.getId();
        return new OrderDetail(o.getId(), o.getOrderNumber(), o.getStatus(), o.getPlacedAt(),
                refs.user(o.getCustomer()), refs.seller(o.getSeller()), mapper.toAddress(o.getShippingAddress()),
                o.getSubtotal(), o.getShippingFee(), o.getTax(), o.getTotal(), o.getCurrency(),
                o.getItems().stream().map(mapper::toItem).toList(),
                paymentRepository.findByOrderIdOrderByCreatedAtAsc(id).stream().map(mapper::toPayment).toList(),
                shipmentRepository.findByOrderIdOrderByIdAsc(id).stream().map(mapper::toShipment).toList(),
                returnRepository.findByOrderIdOrderByOpenedAtAsc(id).stream().map(returnMapper::toReturn).toList(),
                refundRepository.findByOrderIdOrderByCreatedAtAsc(id).stream().map(returnMapper::toRefund).toList());
    }

    /** What the caller may do with the order now (cancel, change address, return), with reasons when not. */
    public OrderActions actions(String idOrNumber) {
        return rules.actions(facts.load(lookup.require(idOrNumber)));
    }

    private Specification<CustomerOrder> spec(OrderSearch s) {
        return Specification.allOf(
                Specs.containsIgnoreCase(s.q(), "orderNumber"),
                Specs.in("status", s.statuses()),
                Specs.eq("customer.id", scope.customerFilter(s.customerId())),
                Specs.eq("seller.id", scope.sellerFilter(s.sellerId())),
                Specs.gte("placedAt", s.from()),
                Specs.lt("placedAt", s.to()),
                hasLineMatching(s.productQuery()));
    }

    /** Some line's title contains every word of the query. */
    private static Specification<CustomerOrder> hasLineMatching(String productQuery) {
        if (productQuery == null || productQuery.isBlank()) return Specs.all();
        String[] words = productQuery.trim().toLowerCase(Locale.ROOT).split("\\s+");
        return (root, query, cb) -> {
            Subquery<Long> sq = query.subquery(Long.class);
            Root<OrderItem> item = sq.from(OrderItem.class);
            Predicate[] parts = new Predicate[words.length + 1];
            parts[0] = cb.equal(item.get("order"), root);
            for (int i = 0; i < words.length; i++) {
                parts[i + 1] = cb.like(cb.lower(item.get("productTitle")), "%" + words[i] + "%");
            }
            return cb.exists(sq.select(item.get("id")).where(parts));
        };
    }
}
