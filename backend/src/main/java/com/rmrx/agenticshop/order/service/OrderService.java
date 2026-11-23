package com.rmrx.agenticshop.order.service;

import com.rmrx.agenticshop.common.dto.PageResponse;
import com.rmrx.agenticshop.common.dto.RefMapper;
import com.rmrx.agenticshop.common.exception.NotFoundException;
import com.rmrx.agenticshop.common.repository.Specs;
import com.rmrx.agenticshop.order.dto.OrderDtos.OrderDetail;
import com.rmrx.agenticshop.order.dto.OrderDtos.OrderSummary;
import com.rmrx.agenticshop.order.entity.CustomerOrder;
import com.rmrx.agenticshop.order.entity.OrderStatus;
import com.rmrx.agenticshop.order.repository.CustomerOrderRepository;
import com.rmrx.agenticshop.order.repository.PaymentRepository;
import com.rmrx.agenticshop.order.repository.ShipmentRepository;
import com.rmrx.agenticshop.returns.repository.RefundRepository;
import com.rmrx.agenticshop.returns.repository.ReturnRequestRepository;
import com.rmrx.agenticshop.returns.service.ReturnMapper;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

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

    public OrderService(CustomerOrderRepository orderRepository, PaymentRepository paymentRepository,
                        ShipmentRepository shipmentRepository, ReturnRequestRepository returnRepository,
                        RefundRepository refundRepository, OrderMapper mapper, ReturnMapper returnMapper,
                        RefMapper refs) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.shipmentRepository = shipmentRepository;
        this.returnRepository = returnRepository;
        this.refundRepository = refundRepository;
        this.mapper = mapper;
        this.returnMapper = returnMapper;
        this.refs = refs;
    }

    public PageResponse<OrderSummary> search(String q, OrderStatus status, Long customerId, Long sellerId,
                                             Instant from, Instant to, Pageable pageable) {
        Specification<CustomerOrder> spec = Specification.allOf(
                Specs.containsIgnoreCase(q, "orderNumber"),
                Specs.eq("status", status),
                Specs.eq("customer.id", customerId),
                Specs.eq("seller.id", sellerId),
                Specs.gte("placedAt", from),
                Specs.lt("placedAt", to));
        return PageResponse.of(orderRepository.findAll(spec, pageable), mapper::toSummary);
    }

    /** Full order view: lines, payments, shipments, returns and refunds. Accepts the id or the order number. */
    public OrderDetail get(String idOrNumber) {
        CustomerOrder o = (idOrNumber.chars().allMatch(Character::isDigit)
                ? orderRepository.findDetailedById(Long.parseLong(idOrNumber))
                : orderRepository.findByOrderNumber(idOrNumber))
                .orElseThrow(() -> new NotFoundException("Order", idOrNumber));
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
}
