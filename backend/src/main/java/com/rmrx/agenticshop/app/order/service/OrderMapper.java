package com.rmrx.agenticshop.app.order.service;

import com.rmrx.agenticshop.app.common.dto.RefMapper;
import com.rmrx.agenticshop.app.order.dto.OrderDtos.*;
import com.rmrx.agenticshop.app.order.entity.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(uses = RefMapper.class)
public interface OrderMapper {

    @Mapping(target = "itemCount", expression = "java(order.getItems().size())")
    OrderSummary toSummary(CustomerOrder order);

    AddressDto toAddress(ShippingAddress address);

    @Mapping(target = "productId", source = "product.id")
    OrderItemDto toItem(OrderItem item);

    PaymentDto toPayment(Payment payment);

    ShipmentDto toShipment(Shipment shipment);
}
