package com.rmrx.agenticshop.ai.action;

import com.rmrx.agenticshop.ai.action.entity.ActionOutcome;
import com.rmrx.agenticshop.ai.action.entity.ActionTargetType;
import com.rmrx.agenticshop.ai.action.entity.AiAction;
import com.rmrx.agenticshop.ai.action.repository.AiActionRepository;
import com.rmrx.agenticshop.ai.context.AgentContext;
import com.rmrx.agenticshop.ai.contract.response.ActionState;
import com.rmrx.agenticshop.ai.contract.response.ActionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lifecycle of issued actions: tie them to the saved reply, expire overdue ones, show their states on a ticket
 * timeline, and the atomic use of a confirmation pair (CONFIRM and DECLINE of one pending write share a
 * {@code pending} id in their params; using either marks both USED).
 */
@Service
public class ActionService {

    /** Params key linking the CONFIRM and DECLINE rows of one pending write. */
    public static final String PENDING = "pending";
    /** Params key holding the pending write's operation (CANCEL_ORDER, START_RETURN, REQUEST_REFUND). */
    public static final String OP = "op";

    /** An action's state for a timeline; never carries the token or its hash. */
    public record ActionStateView(Long id, ActionType type, ActionTargetType targetType, Long targetId,
                                  boolean requiresConfirmation, ActionState state, ActionOutcome outcome,
                                  String outcomeDetail, Long userId, Long messageId, Instant createdAt,
                                  Instant expiresAt, Instant usedAt, JsonNode params) {

        static ActionStateView of(AiAction a) {
            return new ActionStateView(a.getId(), a.getActionType(), a.getTargetType(), a.getTargetId(),
                    a.isRequiresConfirmation(), a.getState(), a.getOutcome(), a.getOutcomeDetail(), a.getUserId(),
                    a.getMessageId(), a.getCreatedAt(), a.getExpiresAt(), a.getUsedAt(), a.getParams());
        }
    }

    private final AiActionRepository repository;
    private final ActionTokenService tokens;

    public ActionService(AiActionRepository repository, ActionTokenService tokens) {
        this.repository = repository;
        this.tokens = tokens;
    }

    /** Ties the actions of a reply to its message once the reply is persisted; already tied ones are kept. */
    public int markMessage(Collection<Long> actionIds, Long messageId) {
        if (actionIds == null || actionIds.isEmpty()) return 0;
        return repository.attachToMessage(actionIds, messageId);
    }

    /** Marks every overdue OPEN action EXPIRED. Optional: {@link ActionTokenService#resolve} expires lazily. */
    public int expireStale() {
        return repository.expireAllBefore(tokens.now());
    }

    public int expireStale(Long ticketId) {
        return repository.expireTicketBefore(ticketId, tokens.now());
    }

    /** All actions of a ticket, oldest first, with overdue ones shown as EXPIRED. */
    public List<ActionStateView> byTicket(Long ticketId) {
        expireStale(ticketId);
        return repository.findByTicketIdOrderByIdAsc(ticketId).stream().map(ActionStateView::of).toList();
    }

    /**
     * State per raw token, only for the caller's own tokens in the caller's ticket; others are left out, exactly as
     * unknown ones, so this cannot be used to probe foreign tokens.
     */
    public Map<String, ActionStateView> stateOf(Collection<String> rawTokens, AgentContext ctx) {
        if (ctx.ticketId() != null) expireStale(ctx.ticketId());
        Map<String, String> tokenByHash = rawTokens.stream().filter(Objects::nonNull).distinct()
                .collect(Collectors.toMap(ActionTokenService::hash, Function.identity()));
        return repository.findByTokenHashIn(tokenByHash.keySet()).stream()
                .filter(a -> Objects.equals(a.getUserId(), ctx.userId())
                        && Objects.equals(a.getTicketId(), ctx.ticketId()))
                .collect(Collectors.toMap(a -> tokenByHash.get(a.getTokenHash()), ActionStateView::of));
    }

    /** Supersedes the OPEN confirmation pairs of the same operation on the same target in this ticket. */
    @Transactional
    public int supersedeConfirmations(Long ticketId, ActionType op, ActionTargetType targetType, Long targetId) {
        int n = 0;
        for (AiAction a : repository.findOpenByTarget(targetType, targetId)) {
            boolean pair = a.getActionType() == ActionType.CONFIRM || a.getActionType() == ActionType.DECLINE;
            if (pair && Objects.equals(a.getTicketId(), ticketId) && op.name().equals(text(a.getParams(), OP))) {
                a.supersede();
                n++;
            }
        }
        return n;
    }

    /**
     * Uses one token of a confirmation pair: the used row gets {@code outcome}, its sibling(s) are marked USED with
     * {@code siblingOutcome}. Returns false if the row was no longer OPEN (used, superseded or expired meanwhile),
     * so concurrent clicks run the write at most once.
     */
    @Transactional
    public boolean claimPair(AiAction used, ActionOutcome outcome, ActionOutcome siblingOutcome, String siblingDetail) {
        Instant now = tokens.now();
        if (repository.claim(used.getId(), outcome, null, now) != 1) return false;
        String pending = text(used.getParams(), PENDING);
        if (pending != null) {
            List<Long> siblings = repository.findByPending(used.getTicketId(), pending).stream()
                    .map(AiAction::getId).filter(id -> !id.equals(used.getId())).toList();
            if (!siblings.isEmpty()) repository.useOpen(siblings, siblingOutcome, siblingDetail, now);
        }
        return true;
    }

    public void recordOutcome(Long actionId, ActionOutcome outcome, String detail) {
        repository.recordOutcome(actionId, outcome, detail);
    }

    static String text(JsonNode params, String key) {
        if (params == null) return null;
        JsonNode v = params.get(key);
        return v == null || v.isNull() ? null : v.asString();
    }
}
