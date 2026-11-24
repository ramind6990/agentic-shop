package com.rmrx.agenticshop.ai.contract.response;

/** A quick-reply chip: label is shown, text is sent as the user's next message. */
public record SuggestedReply(String label, String text) {
}
