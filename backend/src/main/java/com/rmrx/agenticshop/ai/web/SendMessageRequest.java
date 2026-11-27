package com.rmrx.agenticshop.ai.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A new message from the user.
 */
public record SendMessageRequest(@NotBlank @Size(max = 4000) String text) {
}
