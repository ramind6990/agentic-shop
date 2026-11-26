package com.rmrx.agenticshop.ai2.contract.interaction;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * The user's answer to a {@code PendingInteraction}, stored in the run state under the tool call id. Written by the
 * caller before resuming the run. In JSON the kind is the {@code type} property:
 * {@code choice | input | yesNo | approval | dismissed}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = HumanAnswer.ChoiceAnswer.class, name = "choice"),
        @JsonSubTypes.Type(value = HumanAnswer.InputAnswer.class, name = "input"),
        @JsonSubTypes.Type(value = HumanAnswer.YesNoAnswer.class, name = "yesNo"),
        @JsonSubTypes.Type(value = HumanAnswer.ApprovalDecision.class, name = "approval"),
        @JsonSubTypes.Type(value = HumanAnswer.Dismissed.class, name = "dismissed")})
public sealed interface HumanAnswer extends Serializable {

    /** The compact JSON the model gets as the tool result of an ask call. */
    String toToolResult();

    record ChoiceAnswer(List<String> values) implements HumanAnswer {

        public ChoiceAnswer {
            values = values == null ? List.of() : List.copyOf(values);
        }

        @Override
        public String toToolResult() {
            return InteractionJson.write(Map.of("selected", values));
        }
    }

    record InputAnswer(String text) implements HumanAnswer {

        @Override
        public String toToolResult() {
            return InteractionJson.write(Map.of("text", text));
        }
    }

    record YesNoAnswer(boolean yes) implements HumanAnswer {

        @Override
        public String toToolResult() {
            return InteractionJson.write(Map.of("yes", yes));
        }
    }

    /** The answer to an {@code PendingInteraction.ApprovalRequest}. */
    record ApprovalDecision(boolean approved) implements HumanAnswer {

        /** The result of a call the user did not approve (denied, or the approval was dismissed). */
        public static final String DENIED_RESULT =
                "The user denied this action, so it was not executed. Do not retry it unless the user asks for it again.";

        /** Only used when denied; an approved call's result is the real tool output. */
        @Override
        public String toToolResult() {
            return approved ? "The user approved this action." : DENIED_RESULT;
        }
    }

    /** The user closed the question or approval without answering; for an approval it means denied. */
    record Dismissed() implements HumanAnswer {

        @Override
        public String toToolResult() {
            return InteractionJson.write(Map.of("dismissed", true));
        }
    }
}
