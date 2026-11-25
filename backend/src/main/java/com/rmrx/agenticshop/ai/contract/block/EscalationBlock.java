package com.rmrx.agenticshop.ai.contract.block;

import com.rmrx.agenticshop.app.support.entity.SupportTier;

/** Tells the user the conversation was handed to a human, on which ticket and tier, and when to expect a reply. */
public record EscalationBlock(String id, String reason, String ticketNumber, SupportTier tier,
                              String expectedResponse) implements Block {
}
