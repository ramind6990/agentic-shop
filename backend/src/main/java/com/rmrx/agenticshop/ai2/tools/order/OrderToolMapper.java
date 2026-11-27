package com.rmrx.agenticshop.ai2.tools.order;

import com.rmrx.agenticshop.ai2.contract.domain.Address;
import com.rmrx.agenticshop.ai2.contract.domain.OrderStatus;
import com.rmrx.agenticshop.ai2.contract.tool.response.order.OrderCancellationResponse;
import com.rmrx.agenticshop.ai2.contract.tool.response.order.OrderDetailResponse;
import com.rmrx.agenticshop.ai2.contract.tool.response.order.OrderItem;
import com.rmrx.agenticshop.ai2.contract.tool.response.order.OrderLine;
import com.rmrx.agenticshop.app.order.dto.OrderDtos.*;
import com.rmrx.agenticshop.app.returns.dto.ReturnDtos.RefundDto;
import com.rmrx.agenticshop.app.returns.dto.ReturnDtos.ReturnDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/** Order DTOs → ai2 order contract, and the contract's filter values → domain values. */
@Mapper
public interface OrderToolMapper {

    @Mapping(target = "orderNumber", source = "order.orderNumber")
    @Mapping(target = "placedAt", source = "order.placedAt")
    @Mapping(target = "status", source = "order.status")
    @Mapping(target = "total", source = "order.total")
    @Mapping(target = "currency", source = "order.currency")
    @Mapping(target = "seller", source = "order.seller.name")
    @Mapping(target = "lines", source = "items")
    @Mapping(target = "shippingCity", source = "shippingAddress.city")
    @Mapping(target = "trackingNumber", source = "latestShipment.trackingNumber")
    @Mapping(target = "estimatedDelivery", source = "latestShipment.estimatedDelivery")
    OrderItem toCard(OrderOverview overview);

    @Mapping(target = "seller", source = "seller.name")
    @Mapping(target = "lines", source = "items")
    @Mapping(target = "shippingCity", source = "shippingAddress.city")
    @Mapping(target = "trackingNumber", expression = "java(last(d.shipments()) == null ? null : last(d.shipments()).trackingNumber())")
    @Mapping(target = "estimatedDelivery", expression = "java(last(d.shipments()) == null ? null : last(d.shipments()).estimatedDelivery())")
    OrderItem toCard(OrderDetail d);

    @Mapping(target = "itemId", source = "id")
    @Mapping(target = "title", source = "productTitle")
    OrderLine toLine(OrderItemDto item);

    OrderDetailResponse.Payment toPayment(PaymentDto payment);

    OrderDetailResponse.Shipment toShipment(ShipmentDto shipment);

    @Mapping(target = "itemId", source = "orderItemId")
    OrderDetailResponse.Return toReturn(ReturnDto r);

    OrderDetailResponse.Refund toRefund(RefundDto r);

    OrderDetailResponse.Actions toActions(OrderActions actions);

    @Mapping(target = "defaultAddress", constant = "false")
    Address toAddress(AddressDto address);

    OrderCancellationResponse toCancellation(OrderCancellation cancellation);

    List<com.rmrx.agenticshop.app.order.entity.OrderStatus> toDomain(List<OrderStatus> statuses);

    default OrderDetailResponse toDetail(OrderDetail d, OrderActions actions) {
        return new OrderDetailResponse(toCard(d), d.customer().id(), d.customer().name(),
                toAddress(d.shippingAddress()), d.subtotal(), d.shippingFee(), d.tax(),
                d.payments().stream().map(this::toPayment).toList(),
                d.shipments().stream().map(this::toShipment).toList(),
                d.returns().stream().map(this::toReturn).toList(),
                d.refunds().stream().map(this::toRefund).toList(),
                toActions(actions));
    }

    default ShipmentDto last(List<ShipmentDto> shipments) {
        return shipments.isEmpty() ? null : shipments.getLast();
    }
}
