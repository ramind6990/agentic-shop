package com.rmrx.agenticshop.ai.graph.agent.pending;

import com.rmrx.agenticshop.ai.contract.interaction.HumanAnswer;
import com.rmrx.agenticshop.ai.tools.ask.ChoiceOption;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * What a paused run waits for: one question or approval. It is put in the interrupt metadata by the pausing node and
 * kept on the conversation's active run; the UI answers it by {@link #interactionId()}. In JSON the kind is the
 * {@code type} property: {@code choice | input | yesNo | approval}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = PendingInteraction.ChoiceQuestion.class, name = "choice"),
        @JsonSubTypes.Type(value = PendingInteraction.InputQuestion.class, name = "input"),
        @JsonSubTypes.Type(value = PendingInteraction.YesNoQuestion.class, name = "yesNo"),
        @JsonSubTypes.Type(value = PendingInteraction.ApprovalRequest.class, name = "approval")})
public sealed interface PendingInteraction extends Serializable {

    /** Metadata key of the pending interaction in {@code InterruptionMetadata}. */
    String METADATA_KEY = "interaction";

    /** The id the UI sends back with the answer. Stable for a given run and call (see {@link #idFor}). */
    UUID interactionId();

    /** The model's tool call this interaction belongs to; its answer becomes that call's result. */
    String callId();

    /** Whether the answer fits this interaction (type, option values). Dismissed fits every interaction. */
    boolean accepts(HumanAnswer answer);

    /** What the customer is asked: the question, or the approval's title. */
    default String promptText() {
        return switch (this) {
            case ChoiceQuestion q -> q.question();
            case InputQuestion q -> q.question();
            case YesNoQuestion q -> q.question();
            case ApprovalRequest a -> a.title();
        };
    }

    /**
     * A deterministic id: langgraph4j calls {@code interrupt()} again every time the node is entered (e.g. a run
     * recovered from its checkpoint), and the same pending call must keep the id the UI already has.
     */
    static UUID idFor(UUID runId, String callId) {
        return UUID.nameUUIDFromBytes((runId + ":" + callId).getBytes(StandardCharsets.UTF_8));
    }

    record ChoiceQuestion(UUID interactionId, String callId, String question, List<ChoiceOption> options,
                          boolean multiSelect) implements PendingInteraction {

        public ChoiceQuestion {
            options = List.copyOf(options);
        }

        @Override
        public boolean accepts(HumanAnswer answer) {
            if (answer instanceof HumanAnswer.Dismissed) return true;
            if (!(answer instanceof HumanAnswer.ChoiceAnswer(List<String> values))) return false;
            if (values.isEmpty() || (!multiSelect && values.size() > 1)) return false;
            if (new HashSet<>(values).size() != values.size()) return false;
            return options.stream().map(ChoiceOption::value).toList().containsAll(values);
        }
    }

    record InputQuestion(UUID interactionId, String callId, String question) implements PendingInteraction {

        @Override
        public boolean accepts(HumanAnswer answer) {
            return answer instanceof HumanAnswer.Dismissed
                    || answer instanceof HumanAnswer.InputAnswer(String text) && text != null && !text.isBlank();
        }
    }

    record YesNoQuestion(UUID interactionId, String callId, String question) implements PendingInteraction {

        @Override
        public boolean accepts(HumanAnswer answer) {
            return answer instanceof HumanAnswer.Dismissed || answer instanceof HumanAnswer.YesNoAnswer;
        }
    }

    /** A sensitive tool call waiting for the user's approval. Title and detail come from the tool, not the model. */
    record ApprovalRequest(UUID interactionId, String callId, String title, String detail)
            implements PendingInteraction {

        @Override
        public boolean accepts(HumanAnswer answer) {
            return answer instanceof HumanAnswer.Dismissed || answer instanceof HumanAnswer.ApprovalDecision;
        }
    }
}
