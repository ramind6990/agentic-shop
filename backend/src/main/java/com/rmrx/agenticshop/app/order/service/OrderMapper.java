package com.rmrx.agenticshop.app.order.service;

import com.rmrx.agenticshop.app.common.dto.RefMapper;
import com.rmrx.agenticshop.app.order.dto.OrderDtos.*;
import com.rmrx.agenticshop.app.order.entity.*;
import com.rmrx.agenticshop.app.returns.dto.ReturnDtos.OrderRef;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(uses = RefMapper.class)
public interface OrderMapper {

    @Mapping(target = "itemCount", expression = "java(order.getItems().size())")
    OrderSummary toSummary(CustomerOrder order);

    AddressDto toAddress(ShippingAddress address);

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "thumbnailUrl", expression = "java(thumbnail(item))")
    OrderItemDto toItem(OrderItem item);

    PaymentDto toPayment(Payment payment);

    ShipmentDto toShipment(Shipment shipment);

    ShipmentEventDto toEvent(ShipmentEvent event);

    default OrderRef toOrderRef(CustomerOrder o) {
        return o == null ? null : new OrderRef(o.getId(), o.getOrderNumber());
    }

    default String thumbnail(OrderItem item) {
        var images = item.getProduct().getImages();
        return images.isEmpty() ? null : images.getFirst().getUrl();
    }
}
