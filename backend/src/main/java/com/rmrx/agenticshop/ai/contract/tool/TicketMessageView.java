package com.rmrx.agenticshop.ai.contract.tool;

import com.rmrx.agenticshop.app.support.entity.AuthorType;

import java.time.Instant;

/** One message of a support ticket; internal notes are only returned to staff. */
public record TicketMessageView(Long id, AuthorType authorType, String author, String body, boolean internal,
                                Instant at) {
}
