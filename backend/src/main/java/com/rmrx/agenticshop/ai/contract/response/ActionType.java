package com.rmrx.agenticshop.ai.contract.response;

/** What an {@link ItemAction} does when the user clicks it. */
public enum ActionType {
    VIEW_ORDER,
    TRACK_SHIPMENT,
    CANCEL_ORDER,
    START_RETURN,
    REQUEST_REFUND,
    VIEW_PRODUCT,
    ASK_ABOUT_PRODUCT,
    CONFIRM,
    DECLINE,
    SELECT_OPTION,
    TALK_TO_HUMAN
}
