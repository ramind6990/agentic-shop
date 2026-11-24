package com.rmrx.agenticshop.ai.action.entity;

import com.rmrx.agenticshop.ai.contract.response.ActionState;
import com.rmrx.agenticshop.ai.contract.response.ActionType;
import com.rmrx.agenticshop.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * A clickable action or confirmation offered in the chat. Only the SHA-256 hex of the token is stored; the raw
 * token goes to the client once. Only userId may use it, and only while state is OPEN and before expiresAt.
 */
@Getter
@Entity
@Table(name = "ai_action")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiAction extends BaseEntity {

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    /** Set once the agent reply carrying this action is saved. */
    @Column(name = "message_id")
    private Long messageId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    private ActionType actionType;

    /** Null for actions without a record target, such as TALK_TO_HUMAN. */
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type")
    private ActionTargetType targetType;

    @Column(name = "target_id")
    private Long targetId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode params;

    @Column(name = "requires_confirmation", nullable = false)
    private boolean requiresConfirmation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActionState state;

    @Enumerated(EnumType.STRING)
    private ActionOutcome outcome;

    @Column(name = "outcome_detail", columnDefinition = "text")
    private String outcomeDetail;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "used_at")
    private Instant usedAt;

    public static AiAction open(String tokenHash, Long ticketId, Long userId, ActionType actionType,
                                ActionTargetType targetType, Long targetId, JsonNode params,
                                boolean requiresConfirmation, Instant expiresAt, Instant now) {
        AiAction action = new AiAction();
        action.tokenHash = tokenHash;
        action.ticketId = ticketId;
        action.userId = userId;
        action.actionType = actionType;
        action.targetType = targetType;
        action.targetId = targetId;
        action.params = params;
        action.requiresConfirmation = requiresConfirmation;
        action.state = ActionState.OPEN;
        action.expiresAt = expiresAt;
        action.createdAt = now;
        return action;
    }

    public void attachToMessage(Long messageId) {
        this.messageId = messageId;
    }

    public boolean isUsable(Instant now) {
        return state == ActionState.OPEN && now.isBefore(expiresAt);
    }

    public void markUsed(ActionOutcome outcome, String detail, Instant now) {
        requireOpen();
        this.state = ActionState.USED;
        this.outcome = outcome;
        this.outcomeDetail = detail;
        this.usedAt = now;
    }

    public void expire() {
        requireOpen();
        this.state = ActionState.EXPIRED;
    }

    public void supersede() {
        requireOpen();
        this.state = ActionState.SUPERSEDED;
    }

    private void requireOpen() {
        if (state != ActionState.OPEN) throw new IllegalStateException("action is " + state);
    }
}
