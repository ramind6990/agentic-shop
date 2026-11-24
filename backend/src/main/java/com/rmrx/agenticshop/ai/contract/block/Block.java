package com.rmrx.agenticshop.ai.contract.block;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * A structured part of an agent reply the chat UI renders as a card, list or prompt. id is the tool resultId the
 * block was built from, so the UI and traces can tie it back to verified data. Serialized with a {@code type} field.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = OrderListBlock.class, name = "ORDER_LIST"),
        @JsonSubTypes.Type(value = OrderDetailBlock.class, name = "ORDER_DETAIL"),
        @JsonSubTypes.Type(value = ShipmentTrackingBlock.class, name = "SHIPMENT_TRACKING"),
        @JsonSubTypes.Type(value = ProductListBlock.class, name = "PRODUCT_LIST"),
        @JsonSubTypes.Type(value = ProductDetailBlock.class, name = "PRODUCT_DETAIL"),
        @JsonSubTypes.Type(value = ReturnStatusBlock.class, name = "RETURN_STATUS"),
        @JsonSubTypes.Type(value = RefundResultBlock.class, name = "REFUND_RESULT"),
        @JsonSubTypes.Type(value = PolicyAnswerBlock.class, name = "POLICY_ANSWER"),
        @JsonSubTypes.Type(value = ConfirmationRequestBlock.class, name = "CONFIRMATION_REQUEST"),
        @JsonSubTypes.Type(value = ChoiceRequestBlock.class, name = "CHOICE_REQUEST"),
        @JsonSubTypes.Type(value = EscalationBlock.class, name = "ESCALATION")
})
public sealed interface Block permits OrderListBlock, OrderDetailBlock, ShipmentTrackingBlock, ProductListBlock,
        ProductDetailBlock, ReturnStatusBlock, RefundResultBlock, PolicyAnswerBlock, ConfirmationRequestBlock,
        ChoiceRequestBlock, EscalationBlock {

    String id();
}
