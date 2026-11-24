package com.rmrx.agenticshop.support.entity;

/**
 * Shape of a ticket message. TEXT is a plain body; ENVELOPE carries a structured agent reply in the payload;
 * ACTION records an action or confirmation outcome; SYSTEM is an automated notice.
 */
public enum MessageKind {
    TEXT,
    ENVELOPE,
    ACTION,
    SYSTEM
}
