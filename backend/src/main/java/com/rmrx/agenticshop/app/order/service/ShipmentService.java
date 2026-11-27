package com.rmrx.agenticshop.app.order.service;

import com.rmrx.agenticshop.app.common.dto.PageResponse;
import com.rmrx.agenticshop.app.common.dto.RefMapper;
import com.rmrx.agenticshop.app.common.exception.BadRequestException;
import com.rmrx.agenticshop.app.common.exception.NotFoundException;
import com.rmrx.agenticshop.app.common.repository.Specs;
import com.rmrx.agenticshop.app.order.dto.OrderDtos.ShipmentListItem;
import com.rmrx.agenticshop.app.order.dto.OrderDtos.ShipmentTracking;
import com.rmrx.agenticshop.app.order.dto.OrderDtos.TrackedShipment;
import com.rmrx.agenticshop.app.order.entity.CustomerOrder;
import com.rmrx.agenticshop.app.order.entity.Shipment;
import com.rmrx.agenticshop.app.order.entity.ShipmentEvent;
import com.rmrx.agenticshop.app.order.entity.ShipmentStatus;
import com.rmrx.agenticshop.app.order.repository.ShipmentEventRepository;
import com.rmrx.agenticshop.app.order.repository.ShipmentRepository;
import com.rmrx.agenticshop.app.security.AccessScope;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Where parcels are: an order's shipments with their carrier scans, and a shipment search for staff. */
@Service
@Transactional(readOnly = true)
public class ShipmentService {

    private final ShipmentRepository shipments;
    private final ShipmentEventRepository events;
    private final OrderLookup lookup;
    private final OrderMapper mapper;
    private final RefMapper refs;
    private final AccessScope scope;
    private final Clock clock;

    public ShipmentService(ShipmentRepository shipments, ShipmentEventRepository events, OrderLookup lookup,
                           OrderMapper mapper, RefMapper refs, AccessScope scope, Clock clock) {
        this.shipments = shipments;
        this.events = events;
        this.lookup = lookup;
        this.mapper = mapper;
        this.refs = refs;
        this.scope = scope;
        this.clock = clock;
    }

    /** Tracking of one order, found by its number or by the tracking number of one of its shipments. */
    public ShipmentTracking track(String orderNumber, String trackingNumber) {
        if ((orderNumber == null) == (trackingNumber == null)) {
            throw new BadRequestException("Give exactly one of orderNumber and trackingNumber.");
        }
        CustomerOrder order = orderNumber != null ? lookup.require(orderNumber) : orderOf(trackingNumber);
        List<Shipment> list = shipments.findByOrderIdOrderByIdAsc(order.getId());
        Map<Long, List<ShipmentEvent>> scans = events.findByShipmentIdInOrderByOccurredAtAsc(
                list.stream().map(Shipment::getId).toList()).stream()
                .collect(Collectors.groupingBy(e -> e.getShipment().getId()));
        return new ShipmentTracking(mapper.toOrderRef(order), order.getStatus(), list.stream()
                .map(s -> new TrackedShipment(mapper.toShipment(s),
                        scans.getOrDefault(s.getId(), List.of()).stream().map(mapper::toEvent).toList()))
                .toList());
    }

    public PageResponse<ShipmentListItem> search(ShipmentSearch s, Pageable pageable) {
        Specification<Shipment> spec = Specification.allOf(
                Specs.eq("carrier", s.carrier()),
                Specs.in("status", s.overdueOnly() ? List.of(ShipmentStatus.IN_TRANSIT) : s.statuses()),
                Specs.gte("shippedAt", s.shippedFrom()),
                Specs.lt("shippedAt", s.shippedTo()),
                Specs.eq("order.customer.id", scope.customerFilter(s.customerId())),
                Specs.eq("order.seller.id", scope.sellerFilter(s.sellerId())),
                s.overdueOnly() ? Specs.lt("estimatedDelivery", LocalDate.now(clock.withZone(ZoneOffset.UTC)))
                        : Specs.all());
        return PageResponse.of(shipments.findAll(spec, pageable), sh -> new ShipmentListItem(mapper.toShipment(sh),
                mapper.toOrderRef(sh.getOrder()), refs.user(sh.getOrder().getCustomer()),
                refs.seller(sh.getOrder().getSeller())));
    }

    private CustomerOrder orderOf(String trackingNumber) {
        Shipment shipment = shipments.findFirstByTrackingNumberIgnoreCase(trackingNumber.trim())
                .orElseThrow(() -> new NotFoundException("Shipment", trackingNumber));
        CustomerOrder order = shipment.getOrder();
        scope.requireOwned(order.getCustomer().getId(), order.getSeller().getId(), "Shipment", trackingNumber);
        return order;
    }
}
