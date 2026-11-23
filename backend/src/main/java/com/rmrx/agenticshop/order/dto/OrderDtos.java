package com.rmrx.agenticshop.order.dto;

import com.rmrx.agenticshop.common.dto.Ref;
import com.rmrx.agenticshop.order.entity.*;
import com.rmrx.agenticshop.returns.dto.ReturnDtos.RefundDto;
import com.rmrx.agenticshop.returns.dto.ReturnDtos.ReturnDto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record OrderSummary(Long id, String orderNumber, OrderStatus status, Instant placedAt,
                               BigDecimal total, String currency, Ref customer, Ref seller, int itemCount) {
    }

    public record AddressDto(String recipient, String line1, String line2, String city, String region,
                             String postalCode, String country) {
    }

    public record OrderItemDto(Long id, Long productId, String productTitle, String sku, BigDecimal unitPrice,
                               int quantity, BigDecimal lineTotal) {
    }

    public record PaymentDto(Long id, PaymentMethod method, PaymentStatus status, BigDecimal amount,
                             String providerRef, Instant createdAt) {
    }

    public record ShipmentDto(Long id, Carrier carrier, String trackingNumber, ShipmentStatus status,
                              Instant shippedAt, Instant deliveredAt) {
    }

    public record OrderDetail(Long id, String orderNumber, OrderStatus status, Instant placedAt,
                              Ref customer, Ref seller, AddressDto shippingAddress,
                              BigDecimal subtotal, BigDecimal shippingFee, BigDecimal tax, BigDecimal total,
                              String currency, List<OrderItemDto> items, List<PaymentDto> payments,
                              List<ShipmentDto> shipments, List<ReturnDto> returns, List<RefundDto> refunds) {
    }
}
