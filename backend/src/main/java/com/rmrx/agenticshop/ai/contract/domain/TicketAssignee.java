package com.rmrx.agenticshop.ai.contract.domain;

/** Whose tickets a staff search lists: the caller's own, nobody's yet, or anyone's. */
public enum TicketAssignee {
    ME,
    UNASSIGNED,
    ANY
}
