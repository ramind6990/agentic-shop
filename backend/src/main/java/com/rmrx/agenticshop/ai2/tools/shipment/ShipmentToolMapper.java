package com.rmrx.agenticshop.ai2.tools.shipment;

import com.rmrx.agenticshop.ai2.contract.domain.Carrier;
import com.rmrx.agenticshop.ai2.contract.domain.ShipmentStatus;
import com.rmrx.agenticshop.ai2.contract.tool.response.shipment.ShipmentListResponse;
import com.rmrx.agenticshop.ai2.contract.tool.response.shipment.TrackingResponse;
import com.rmrx.agenticshop.app.order.dto.OrderDtos.ShipmentEventDto;
import com.rmrx.agenticshop.app.order.dto.OrderDtos.ShipmentListItem;
import com.rmrx.agenticshop.app.order.dto.OrderDtos.ShipmentTracking;
import com.rmrx.agenticshop.app.order.dto.OrderDtos.TrackedShipment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/** Shipment DTOs → ai2 shipment contract, and the contract's filter values → domain values. */
@Mapper
public interface ShipmentToolMapper {

    @Mapping(target = "orderNumber", source = "order.orderNumber")
    TrackingResponse toTracking(ShipmentTracking tracking);

    @Mapping(target = "carrier", source = "shipment.carrier")
    @Mapping(target = "trackingNumber", source = "shipment.trackingNumber")
    @Mapping(target = "status", source = "shipment.status")
    @Mapping(target = "shippedAt", source = "shipment.shippedAt")
    @Mapping(target = "deliveredAt", source = "shipment.deliveredAt")
    @Mapping(target = "estimatedDelivery", source = "shipment.estimatedDelivery")
    @Mapping(target = "eventCount", expression = "java(tracked.events().size())")
    TrackingResponse.Shipment toShipment(TrackedShipment tracked);

    TrackingResponse.Event toEvent(ShipmentEventDto event);

    @Mapping(target = "orderNumber", source = "order.orderNumber")
    @Mapping(target = "customerId", source = "customer.id")
    @Mapping(target = "customer", source = "customer.name")
    @Mapping(target = "seller", source = "seller.name")
    @Mapping(target = "carrier", source = "shipment.carrier")
    @Mapping(target = "trackingNumber", source = "shipment.trackingNumber")
    @Mapping(target = "status", source = "shipment.status")
    @Mapping(target = "shippedAt", source = "shipment.shippedAt")
    @Mapping(target = "estimatedDelivery", source = "shipment.estimatedDelivery")
    @Mapping(target = "deliveredAt", source = "shipment.deliveredAt")
    ShipmentListResponse.Row toRow(ShipmentListItem item);

    com.rmrx.agenticshop.app.order.entity.Carrier toDomain(Carrier carrier);

    List<com.rmrx.agenticshop.app.order.entity.ShipmentStatus> toDomain(List<ShipmentStatus> statuses);
}
