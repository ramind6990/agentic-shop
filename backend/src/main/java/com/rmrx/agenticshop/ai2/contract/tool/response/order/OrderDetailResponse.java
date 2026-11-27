package com.rmrx.agenticshop.ai2.contract.tool.response.order;

import com.rmrx.agenticshop.ai2.contract.tool.FreeText;
import com.rmrx.agenticshop.ai2.contract.domain.Address;
import com.rmrx.agenticshop.ai2.contract.domain.Carrier;
import com.rmrx.agenticshop.ai2.contract.domain.PaymentMethod;
import com.rmrx.agenticshop.ai2.contract.domain.PaymentStatus;
import com.rmrx.agenticshop.ai2.contract.domain.RefundStatus;
import com.rmrx.agenticshop.ai2.contract.domain.ReturnReason;
import com.rmrx.agenticshop.ai2.contract.domain.ReturnStatus;
import com.rmrx.agenticshop.ai2.contract.domain.ShipmentStatus;
import com.rmrx.agenticshop.ai2.contract.domain.Verdict;
import com.rmrx.agenticshop.ai2.contract.tool.ToolResponse;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Everything about one order, and what can be done with it now ({@link Actions}, computed by the shop's rules).
 */
public record OrderDetailResponse(OrderItem card, Long customerId, String customer, Address shippingAddress,
                                  BigDecimal subtotal, BigDecimal shippingFee, BigDecimal tax, List<Payment> payments,
                                  List<Shipment> shipments, List<Return> returns, List<Refund> refunds,
                                  Actions actions) implements ToolResponse {

    public record Payment(PaymentMethod method, PaymentStatus status, BigDecimal amount) implements Serializable {
    }

    public record Shipment(Carrier carrier, String trackingNumber, ShipmentStatus status, Instant shippedAt,
                           Instant deliveredAt, LocalDate estimatedDelivery) implements Serializable {
    }

    public record Return(Long id, Long itemId, int quantity, ReturnReason reason, ReturnStatus status,
                         Instant openedAt) implements Serializable {
    }

    public record Refund(Long id, BigDecimal amount, RefundStatus status, @FreeText String reason, Instant createdAt)
            implements Serializable {
    }

    /**
     * @param returnDeadline       last moment to open a return (null until delivered)
     * @param returnableQuantities itemId → units that can still be returned
     */
    public record Actions(Verdict cancel, Verdict changeAddress, Verdict returnItems, Instant returnDeadline,
                          Map<Long, Integer> returnableQuantities) implements Serializable {
    }

    /** The full view without image links. */
    public record Summary(String orderNumber, Instant placedAt, String status, String seller, Long customerId,
                          BigDecimal total, String currency, List<Line> lines, Address shippingAddress,
                          List<Payment> payments, List<Shipment> shipments, List<Return> returns,
                          List<Refund> refunds, Actions actions) implements Serializable {
    }

    public record Line(Long itemId, Long productId, String title, int quantity, BigDecimal unitPrice)
            implements Serializable {
    }

    @Override
    public Summary toAgentSummary() {
        return new Summary(card.orderNumber(), card.placedAt(), card.status().name(), card.seller(), customerId,
                card.total(), card.currency(), card.lines().stream()
                .map(l -> new Line(l.itemId(), l.productId(), l.title(), l.quantity(), l.unitPrice())).toList(),
                Address.agentView(shippingAddress), payments, shipments, returns, refunds, actions);
    }

    @Override
    public List<String> itemIdentifiers() {
        return List.of(card.orderNumber());
    }
}
