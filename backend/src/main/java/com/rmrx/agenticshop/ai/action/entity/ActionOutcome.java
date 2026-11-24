package com.rmrx.agenticshop.ai.action.entity;

/**
 * What happened when an action was used. CLICKED: an item button was used; it never writes by itself (a write button
 * leads to a confirmation step).
 */
public enum ActionOutcome {
    CLICKED,
    CONFIRMED,
    DECLINED,
    EXECUTED,
    FAILED,
    REJECTED
}
