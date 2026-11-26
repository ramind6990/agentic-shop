package com.rmrx.agenticshop.ai2.events.ws;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.rmrx.agenticshop.ai2.contract.interaction.HumanAnswer;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * A frame from the client. In JSON the kind is the {@code type} property: {@code chat | subscribe}. requestId is the
 * client's own correlation id, echoed in the {@code ChatResponse} answering the frame (ACCEPTED or ERROR).
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ClientMessage.ChatRequest.class, name = "chat"),
        @JsonSubTypes.Type(value = ClientMessage.SubscribeRequest.class, name = "subscribe")})
public sealed interface ClientMessage {

    String requestId();

    /**
     * A new message ({@code text}; without conversationId a new conversation is created) or the answer to the
     * question or approval the conversation's run is paused on ({@code conversationId + interactionId + answer}).
     */
    record ChatRequest(@Size(max = 64) String requestId, Long conversationId, @Size(max = 4000) String text,
                       UUID interactionId, HumanAnswer answer) implements ClientMessage {

        public boolean answering() {
            return interactionId != null || answer != null;
        }

        @JsonIgnore
        @AssertTrue(message = "send either a non-blank text, or conversationId + interactionId + answer")
        public boolean isWellFormed() {
            boolean send = text != null && !text.isBlank() && !answering();
            boolean answer = text == null && conversationId != null && interactionId != null && this.answer != null;
            return send || answer;
        }
    }

    /** Follow a run's events (e.g. after a reconnect), from the event after afterSeq (0 or absent = all buffered). */
    record SubscribeRequest(@Size(max = 64) String requestId, @NotNull UUID runId, @PositiveOrZero Long afterSeq)
            implements ClientMessage {
    }
}
