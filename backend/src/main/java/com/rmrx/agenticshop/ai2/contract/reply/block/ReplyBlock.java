package com.rmrx.agenticshop.ai2.contract.reply.block;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * The data a reply shows next to its text, built from the tool result the agent chose to display. In JSON the kind is
 * the {@code type} property; each kind has its own UI card.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ProductListBlock.class, name = "PRODUCT_LIST"),
        @JsonSubTypes.Type(value = ProductDetailBlock.class, name = "PRODUCT_DETAIL"),
        @JsonSubTypes.Type(value = ReviewListBlock.class, name = "REVIEW_LIST"),
        @JsonSubTypes.Type(value = OrderListBlock.class, name = "ORDER_LIST"),
        @JsonSubTypes.Type(value = OrderDetailBlock.class, name = "ORDER_DETAIL"),
        @JsonSubTypes.Type(value = OrderCancellationBlock.class, name = "ORDER_CANCELLATION"),
        @JsonSubTypes.Type(value = AddressChangeBlock.class, name = "ADDRESS_CHANGE"),
        @JsonSubTypes.Type(value = TrackingBlock.class, name = "TRACKING"),
        @JsonSubTypes.Type(value = ShipmentListBlock.class, name = "SHIPMENT_LIST"),
        @JsonSubTypes.Type(value = ReturnListBlock.class, name = "RETURN_LIST"),
        @JsonSubTypes.Type(value = RefundListBlock.class, name = "REFUND_LIST"),
        @JsonSubTypes.Type(value = TicketListBlock.class, name = "TICKET_LIST"),
        @JsonSubTypes.Type(value = TicketDetailBlock.class, name = "TICKET_DETAIL"),
        @JsonSubTypes.Type(value = CustomerListBlock.class, name = "CUSTOMER_LIST"),
        @JsonSubTypes.Type(value = CustomerDetailBlock.class, name = "CUSTOMER_DETAIL"),
        @JsonSubTypes.Type(value = AddressListBlock.class, name = "ADDRESS_LIST")})
public sealed interface ReplyBlock permits ProductListBlock, ProductDetailBlock, ReviewListBlock, OrderListBlock,
        OrderDetailBlock, OrderCancellationBlock, AddressChangeBlock, TrackingBlock, ShipmentListBlock,
        ReturnListBlock, RefundListBlock, TicketListBlock, TicketDetailBlock, CustomerListBlock, CustomerDetailBlock,
        AddressListBlock {
}
