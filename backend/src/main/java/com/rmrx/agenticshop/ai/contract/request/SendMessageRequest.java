package com.rmrx.agenticshop.ai.contract.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A user message in an existing conversation. */
public record SendMessageRequest(@NotBlank @Size(max = MAX_TEXT) String text) {

    public static final int MAX_TEXT = 4000;
}
