package com.rmrx.agenticshop.ai2.web;

import com.rmrx.agenticshop.ai2.contract.interaction.HumanAnswer;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * The user's answer to the question or approval the conversation's run is paused on, e.g.
 * {@code {"interactionId": "…", "answer": {"type": "choice", "values": ["WE45"]}}}.
 */
public record AnswerRequest(@NotNull UUID interactionId, @NotNull HumanAnswer answer) {
}
