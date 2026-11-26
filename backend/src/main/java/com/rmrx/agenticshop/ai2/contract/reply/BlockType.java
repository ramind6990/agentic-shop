package com.rmrx.agenticshop.ai2.contract.reply;

/**
 * Which list of items a {@link ReplyEnvelope} carries; NONE when the reply is text only.
 */
public enum BlockType {
    NONE,
    PRODUCT_LIST,
    ORDER_LIST
}
