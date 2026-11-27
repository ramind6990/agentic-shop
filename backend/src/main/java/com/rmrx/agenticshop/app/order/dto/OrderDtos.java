package com.rmrx.agenticshop.app.order.dto;

import com.rmrx.agenticshop.app.common.dto.Ref;
import com.rmrx.agenticshop.app.order.entity.*;
import com.rmrx.agenticshop.app.returns.dto.ReturnDtos.OrderRef;
import com.rmrx.agenticshop.app.returns.dto.ReturnDtos.RefundDto;
import com.rmrx.agenticshop.app.returns.dto.ReturnDtos.ReturnDto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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
                               int quantity, BigDecimal lineTotal, String thumbnailUrl) {
    }

    public record PaymentDto(Long id, PaymentMethod method, PaymentStatus status, BigDecimal amount,
                             String providerRef, Instant createdAt) {
    }

    public record ShipmentDto(Long id, Carrier carrier, String trackingNumber, ShipmentStatus status,
                              Instant shippedAt, Instant deliveredAt, LocalDate estimatedDelivery) {
    }

    public record OrderDetail(Long id, String orderNumber, OrderStatus status, Instant placedAt,
                              Ref customer, Ref seller, AddressDto shippingAddress,
                              BigDecimal subtotal, BigDecimal shippingFee, BigDecimal tax, BigDecimal total,
                              String currency, List<OrderItemDto> items, List<PaymentDto> payments,
                              List<ShipmentDto> shipments, List<ReturnDto> returns, List<RefundDto> refunds) {
    }

    /** An order with what a list card needs: its lines, delivery city and latest shipment. */
    public record OrderOverview(OrderSummary order, AddressDto shippingAddress, List<OrderItemDto> items,
                                ShipmentDto latestShipment) {
    }

    // ---------- actions ----------

    /** Whether an action is allowed now; when not, the reason in plain words. */
    public record Verdict(boolean allowed, String reason) {

        public static Verdict ok() {
            return new Verdict(true, null);
        }

        public static Verdict no(String reason) {
            return new Verdict(false, reason);
        }
    }

    /**
     * What the caller may do with an order now.
     *
     * @param returnDeadline        last moment to open a return (null until delivered)
     * @param returnableQuantities  order item id → units that can still be returned
     */
    public record OrderActions(Verdict cancel, Verdict changeAddress, Verdict returnItems, Instant returnDeadline,
                               Map<Long, Integer> returnableQuantities) {
    }

    /** Result of a cancellation; refundAmount is zero when the payment was only authorized (voided). */
    public record OrderCancellation(String orderNumber, OrderStatus status, Instant cancelledAt,
                                    BigDecimal refundAmount, String currency, PaymentMethod refundMethod,
                                    LocalDate refundExpectedBy) {
    }

    // ---------- tracking ----------

    public record ShipmentEventDto(Instant occurredAt, ShipmentEventType type, String location, String description) {
    }

    public record TrackedShipment(ShipmentDto shipment, List<ShipmentEventDto> events) {
    }

    /** An order's shipments with their scans; empty shipments while the order has not been shipped. */
    public record ShipmentTracking(OrderRef order, OrderStatus orderStatus, List<TrackedShipment> shipments) {
    }

    /** A shipment in a staff search result. */
    public record ShipmentListItem(ShipmentDto shipment, OrderRef order, Ref customer, Ref seller) {
    }
}
