package com.rmrx.agenticshop.ai2.conversation.message;

import com.rmrx.agenticshop.ai2.contract.interaction.HumanAnswer;

/**
 * Where an INTERACTION message is in its life. Questions end ANSWERED / DISMISSED, approvals APPROVED / DENIED; both
 * end NOT_ANSWERED when the run was abandoned.
 */
public enum InteractionStatus {
    PENDING,
    ANSWERED,
    DISMISSED,
    APPROVED,
    DENIED,
    NOT_ANSWERED;

    /** The status an accepted answer gives its interaction. */
    public static InteractionStatus of(HumanAnswer answer) {
        return switch (answer) {
            case HumanAnswer.Dismissed d -> DISMISSED;
            case HumanAnswer.ApprovalDecision(boolean approved) -> approved ? APPROVED : DENIED;
            default -> ANSWERED;
        };
    }
}
