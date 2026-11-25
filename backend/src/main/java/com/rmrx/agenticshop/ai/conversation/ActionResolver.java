package com.rmrx.agenticshop.ai.conversation;

import com.rmrx.agenticshop.ai.action.ActionNotFoundException;
import com.rmrx.agenticshop.ai.action.ActionNotFoundException.Reason;
import com.rmrx.agenticshop.ai.action.ActionService;
import com.rmrx.agenticshop.ai.action.ActionTokenService;
import com.rmrx.agenticshop.ai.action.WriteGateway;
import com.rmrx.agenticshop.ai.action.entity.ActionOutcome;
import com.rmrx.agenticshop.ai.action.entity.ActionTargetType;
import com.rmrx.agenticshop.ai.action.entity.AiAction;
import com.rmrx.agenticshop.ai.action.repository.AiActionRepository;
import com.rmrx.agenticshop.ai.context.AgentContext;
import com.rmrx.agenticshop.ai.contract.request.ActionRequest;
import com.rmrx.agenticshop.ai.contract.request.ActionRequest.Decision;
import com.rmrx.agenticshop.ai.contract.response.ActionState;
import com.rmrx.agenticshop.ai.contract.response.ActionType;
import com.rmrx.agenticshop.ai.contract.tool.OrderDetailView;
import com.rmrx.agenticshop.ai.contract.tool.RefundView;
import com.rmrx.agenticshop.ai.contract.tool.ReturnView;
import com.rmrx.agenticshop.ai.graph.ActionEvent;
import com.rmrx.agenticshop.ai.graph.ActionEvent.Result;
import com.rmrx.agenticshop.ai.tools.ToolData;
import com.rmrx.agenticshop.ai.tools.TurnResultRegistry;
import com.rmrx.agenticshop.app.common.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Uses a chat button for {@code POST /api/ai/actions/{token}} and turns the result into an {@link ActionEvent}.
 *
 * <ul>
 * <li>Confirmation buttons (CONFIRM/DECLINE of a pending write) go through {@link WriteGateway#commit} /
 * {@link WriteGateway#decline}: the atomic claim uses both tokens of the pair once, the port re-checks the current
 * state, so a replay or a double click never writes twice. On EXECUTED the post-write state is registered in the
 * turn's registry, so the reply's block comes from real data, never from model text.</li>
 * <li>Item buttons (VIEW_ORDER, TRACK_SHIPMENT, CANCEL_ORDER, START_RETURN, TALK_TO_HUMAN, …) are claimed USED with
 * outcome CLICKED and never write; the turn then reads, prepares or escalates (see the graph's ActionFollowUp).</li>
 * <li>SELECT_OPTION (an option of a CHOICE_REQUEST) is an item button too: its operation is the stored {@code then}
 * action (e.g. CANCEL_ORDER) and its target the chosen order; the choice's other options are used up with it.</li>
 * <li>The caller's own button that is no longer usable (expired, superseded, already used, or lost a race) runs
 * nothing and becomes a stale event; the turn answers with the current state.</li>
 * </ul>
 *
 * Anything that is not the caller's own token is {@link ActionNotFoundException} (404), whatever the reason.
 */
@Slf4j
@Component
public class ActionResolver {

    private static final Pattern TOKEN_SHAPE = Pattern.compile("[A-Za-z0-9_-]{43}");
    /** Client input an item button may carry (e.g. a return reason); everything else is dropped. */
    private static final Set<String> CLIENT_PARAMS = Set.of("reason", "note", "amount", "option");
    private static final int PARAM_MAX = 500;

    private final AiActionRepository repository;
    private final ActionTokenService tokens;
    private final ActionService actions;
    private final WriteGateway gateway;

    public ActionResolver(AiActionRepository repository, ActionTokenService tokens, ActionService actions,
                          WriteGateway gateway) {
        this.repository = repository;
        this.tokens = tokens;
        this.actions = actions;
        this.gateway = gateway;
    }

    /** The caller's own action behind a raw token, in any state; otherwise {@link ActionNotFoundException}. */
    public AiAction owned(AgentContext ctx, String rawToken) {
        if (rawToken == null || !TOKEN_SHAPE.matcher(rawToken).matches()) throw notFound(Reason.MALFORMED, ctx, null);
        AiAction action = repository.findByTokenHash(ActionTokenService.hash(rawToken)).orElse(null);
        if (action == null) throw notFound(Reason.UNKNOWN, ctx, null);
        if (!Objects.equals(action.getUserId(), ctx.userId())) throw notFound(Reason.WRONG_USER, ctx, action);
        return action;
    }

    /** The decision of a confirmation button (inferred from it when absent); 400 if it contradicts the button. */
    public static Decision decisionOf(AiAction action, ActionRequest req) {
        Decision sent = req == null ? null : req.decision();
        Decision own = switch (action.getActionType()) {
            case CONFIRM -> Decision.CONFIRM;
            case DECLINE -> Decision.DECLINE;
            default -> null;
        };
        if (own == null) {
            if (sent != null) throw new BadRequestException("decision is only valid for confirmation buttons");
            return null;
        }
        if (sent != null && sent != own) {
            throw new BadRequestException("decision " + sent + " does not match this button");
        }
        return own;
    }

    /**
     * Uses the button (ctx bound to the action's ticket). Never throws for a stale button: that becomes an event
     * with result EXPIRED, SUPERSEDED or ALREADY_USED and nothing runs.
     */
    public ActionEvent use(AgentContext ctx, AiAction action, String rawToken, ActionRequest req,
                           TurnResultRegistry registry) {
        Decision decision = decisionOf(action, req);
        ActionEvent base = baseEvent(action, decision, req);
        if (!action.isUsable(tokens.now())) return stale(base, action);
        if (decision == null) {
            // the other options of a choice (same pending group) are used up with it; plain items have no siblings
            if (!actions.claimPair(action, ActionOutcome.CLICKED, ActionOutcome.CLICKED,
                    "another option was chosen")) {
                return stale(base, reload(action));
            }
            return base.withResult(Result.CLICKED, null);
        }
        try {
            if (decision == Decision.DECLINE) {
                WriteGateway.DeclineResult d = gateway.decline(ctx, rawToken);
                return base.withResult(Result.DECLINED, d.message());
            }
            WriteGateway.CommitResult r = gateway.commit(ctx, rawToken, false);
            return switch (r.outcome()) {
                case EXECUTED -> base.withResult(Result.EXECUTED, r.write().message())
                        .withResultIds(List.of(register(r, registry)));
                case REJECTED -> base.withResult(Result.REJECTED, r.write().message());
                default -> base.withResult(Result.FAILED, r.write().message());
            };
        } catch (ActionNotFoundException e) {
            // used, superseded or expired between our check and the claim: nothing was written
            return stale(base, reload(action));
        }
    }

    private ActionEvent baseEvent(AiAction a, Decision decision, ActionRequest req) {
        JsonNode p = a.getParams();
        ActionType operation = a.getActionType();
        Map<String, String> params = new LinkedHashMap<>();
        if (decision != null) {
            String op = text(p, ActionService.OP);
            if (op != null) operation = ActionType.valueOf(op);
            String amount = text(p, "amount");
            if (amount != null) params.put("amount", amount);
        } else if (a.getActionType() == ActionType.SELECT_OPTION) {
            // a choice option continues with the action stored when the choice was offered (never client input)
            String then = text(p, ActionService.THEN);
            operation = then == null ? ActionType.VIEW_ORDER : ActionType.valueOf(then);
        }
        if (decision == null && req != null) {
            req.params().forEach((k, v) -> {
                if (CLIENT_PARAMS.contains(k) && v != null && !v.isBlank()) {
                    params.put(k, v.length() <= PARAM_MAX ? v.strip() : v.substring(0, PARAM_MAX));
                }
            });
        }
        Long orderId = null;
        if (p != null && p.hasNonNull("orderId")) orderId = p.get("orderId").asLong();
        else if (a.getTargetType() == ActionTargetType.ORDER) orderId = a.getTargetId();
        return new ActionEvent(a.getId(), a.getActionType(), decision, operation, a.getTargetType(), a.getTargetId(),
                orderId, null, null, List.of(), params);
    }

    private ActionEvent stale(ActionEvent base, AiAction current) {
        ActionState state = current.getState();
        if (state == ActionState.OPEN && !current.isUsable(tokens.now())) {
            actions.expireStale(current.getTicketId());
            state = ActionState.EXPIRED;
        }
        log.info("Stale action {} used by user {}: state {}", current.getId(), current.getUserId(), state);
        return switch (state) {
            case SUPERSEDED -> base.withResult(Result.SUPERSEDED, "a newer proposal replaced this one");
            case EXPIRED -> base.withResult(Result.EXPIRED, "this button expired");
            default -> base.withResult(Result.ALREADY_USED, "already used"
                    + (current.getOutcome() == null ? "" : " (" + current.getOutcome() + ")"));
        };
    }

    /** The post-write state as a turn result, so BlockFactory builds the block from it like any tool result. */
    private static String register(WriteGateway.CommitResult r, TurnResultRegistry registry) {
        Object state = r.write().state();
        return switch (state) {
            case OrderDetailView v -> registry.register("cancel_order", v);
            case ReturnView ret -> registry.register("start_return",
                    new ToolData.ReturnStatus(ret.orderId(), List.of(ret), 1, List.of(), 0, false));
            case RefundView f -> registry.register("issue_refund",
                    new ToolData.ReturnStatus(f.orderId(), List.of(), 0, List.of(f), 1, false));
            default -> throw new IllegalStateException("unexpected write state " + state);
        };
    }

    private AiAction reload(AiAction a) {
        return repository.findById(a.getId()).orElse(a);
    }

    private ActionNotFoundException notFound(Reason reason, AgentContext ctx, AiAction action) {
        log.info("Action token rejected: reason={} user={} action={}", reason, ctx.userId(),
                action == null ? null : action.getId());
        return new ActionNotFoundException(reason);
    }

    private static String text(JsonNode params, String key) {
        if (params == null) return null;
        JsonNode v = params.get(key);
        return v == null || v.isNull() ? null : v.asString();
    }
}
