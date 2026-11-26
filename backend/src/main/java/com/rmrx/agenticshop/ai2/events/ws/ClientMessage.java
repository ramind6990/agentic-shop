package com.rmrx.agenticshop.ai2.events.ws;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.rmrx.agenticshop.ai2.contract.interaction.HumanAnswer;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * A frame from the client. In JSON the kind is the {@code type} property: {@code chat | cancel}. requestId is the
 * client's own correlation id, echoed in the {@code ChatResponse} it causes (the run's INPUT, or an ERROR refusing it).
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ClientMessage.ChatRequest.class, name = "chat"),
        @JsonSubTypes.Type(value = ClientMessage.CancelRequest.class, name = "cancel")})
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

    /** Stop the conversation's running run. */
    record CancelRequest(@Size(max = 64) String requestId, @NotNull Long conversationId) implements ClientMessage {
    }
}
