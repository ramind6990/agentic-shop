package com.rmrx.agenticshop.ai.contract.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Opens a support conversation with its first message, optionally about one order. */
public record StartConversationRequest(
        @Size(max = 200) String subject,
        @NotBlank @Size(max = SendMessageRequest.MAX_TEXT) String text,
        @Positive Long orderId) {
}
