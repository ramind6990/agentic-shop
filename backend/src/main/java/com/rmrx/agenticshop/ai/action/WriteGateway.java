package com.rmrx.agenticshop.ai.action;

import com.rmrx.agenticshop.ai.action.ActionNotFoundException.Reason;
import com.rmrx.agenticshop.ai.action.ActionTokenService.Issued;
import com.rmrx.agenticshop.ai.action.ActionTokenService.Spec;
import com.rmrx.agenticshop.ai.action.entity.ActionOutcome;
import com.rmrx.agenticshop.ai.action.entity.ActionTargetType;
import com.rmrx.agenticshop.ai.action.entity.AiAction;
import com.rmrx.agenticshop.ai.context.AgentContext;
import com.rmrx.agenticshop.ai.contract.block.Block;
import com.rmrx.agenticshop.ai.contract.block.ConfirmationRequestBlock;
import com.rmrx.agenticshop.ai.contract.block.RefundResultBlock;
import com.rmrx.agenticshop.ai.contract.block.ReturnStatusBlock;
import com.rmrx.agenticshop.ai.contract.response.ActionType;
import com.rmrx.agenticshop.ai.contract.tool.OrderDetailView;
import com.rmrx.agenticshop.ai.contract.tool.RefundView;
import com.rmrx.agenticshop.ai.contract.tool.ReturnView;
import com.rmrx.agenticshop.ai.contract.tool.ToolErrorCode;
import com.rmrx.agenticshop.ai.contract.tool.WriteOutcome;
import com.rmrx.agenticshop.ai.tools.port.OrderPort;
import com.rmrx.agenticshop.ai.tools.port.PortException;
import com.rmrx.agenticshop.ai.tools.port.RefundPort;
import com.rmrx.agenticshop.ai.tools.port.ReturnPort;
import com.rmrx.agenticshop.ai.tools.port.WritePreview;
import com.rmrx.agenticshop.returns.entity.ReturnReason;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The only path to state-changing writes, in two steps.
 *
 * <ol>
 * <li>{@link #prepare}: checks eligibility with the port's side-effect-free dry run (same rules, same failures as the
 * write, nothing changes). If eligible, supersedes any OPEN confirmation of the same operation on the same target in
 * this ticket and issues a CONFIRM and a DECLINE token (short TTL) that share a {@code pending} id and carry the
 * exact write arguments. Returns a {@link ConfirmationRequestBlock}; otherwise a typed rejection.</li>
 * <li>{@link #commit}: resolves the CONFIRM token, uses both tokens atomically, runs the real port write with the
 * stored arguments and records EXECUTED, REJECTED (the port's rules said no now, e.g. stale state) or FAILED. The port
 * re-checks everything, so a stale confirmation never writes. {@link #decline} uses both tokens as DECLINED.</li>
 * </ol>
 */
@Slf4j
@Service
public class WriteGateway {

    public static final Set<ActionType> OPERATIONS =
            Set.of(ActionType.CANCEL_ORDER, ActionType.START_RETURN, ActionType.REQUEST_REFUND);

    /** Outcome of {@link #prepare}. */
    public sealed interface Prepared {

        record Ready(ConfirmationRequestBlock block, List<Long> actionIds) implements Prepared {
        }

        record Rejected(ToolErrorCode code, String message) implements Prepared {
        }
    }

    /** Outcome of {@link #commit}: the write's result and, when it took effect, a block with the new state. */
    public record CommitResult(ActionType operation, ActionOutcome outcome, WriteOutcome<?> write, Block block) {
    }

    public record DeclineResult(ActionType operation, ActionOutcome outcome, String message) {
    }

    private final OrderPort orders;
    private final ReturnPort returns;
    private final RefundPort refunds;
    private final ActionTokenService tokens;
    private final ActionService actions;
    private final BlockFactory blocks;
    private final JsonMapper json;

    public WriteGateway(OrderPort orders, ReturnPort returns, RefundPort refunds, ActionTokenService tokens,
                        ActionService actions, BlockFactory blocks, JsonMapper json) {
        this.orders = orders;
        this.returns = returns;
        this.refunds = refunds;
        this.tokens = tokens;
        this.actions = actions;
        this.blocks = blocks;
        this.json = json;
    }

    // ------------------------------------------------------------------ prepare

    public Prepared prepare(AgentContext ctx, ActionType op, WriteParams params) {
        if (!OPERATIONS.contains(op)) {
            return new Prepared.Rejected(ToolErrorCode.INVALID_INPUT, op + " is not a confirmable write");
        }
        if (ctx.ticketId() == null) {
            return new Prepared.Rejected(ToolErrorCode.INVALID_INPUT,
                    "no support ticket is bound to this conversation");
        }
        WritePreview preview;
        try {
            preview = switch (op) {
                case CANCEL_ORDER -> orders.checkCancel(ctx, params.orderId());
                case START_RETURN -> returns.checkReturn(ctx, params.orderId(), params.orderItemId(),
                        params.returnReason(), params.note());
                case REQUEST_REFUND -> refunds.checkRefund(ctx, params.orderId(), params.amount(), params.reason());
                default -> throw new IllegalStateException(op.name());
            };
        } catch (PortException e) {
            return new Prepared.Rejected(e.code(), e.getMessage());
        }

        ActionTargetType targetType = op == ActionType.START_RETURN ? ActionTargetType.ORDER_ITEM
                : ActionTargetType.ORDER;
        Long targetId = op == ActionType.START_RETURN ? params.orderItemId() : params.orderId();
        int superseded = actions.supersedeConfirmations(ctx.ticketId(), op, targetType, targetId);
        if (superseded > 0) log.info("Superseded {} open confirmation(s) of {} on {} {}", superseded, op, targetType,
                targetId);

        ObjectNode stored = storedParams(op, params, UUID.randomUUID().toString());
        Text text = text(op, preview);
        List<Issued> pair = tokens.issueAll(ctx, List.of(
                new Spec(ActionType.CONFIRM, text.confirm(), targetType, targetId, stored, true,
                        tokens.confirmationTtl()),
                new Spec(ActionType.DECLINE, text.decline(), targetType, targetId, stored, true,
                        tokens.confirmationTtl())));
        ConfirmationRequestBlock block = new ConfirmationRequestBlock("pending", text.title(), text.description(),
                preview.target(), preview.amount(), preview.currency(), preview.policyReason(),
                pair.getFirst().action().getExpiresAt(), pair.stream().map(Issued::toItemAction).toList());
        return new Prepared.Ready(block, pair.stream().map(i -> i.action().getId()).toList());
    }

    private record Text(String title, String description, String confirm, String decline) {
    }

    private static Text text(ActionType op, WritePreview p) {
        String money = p.amount() == null ? null : p.amount().toPlainString() + " " + p.currency();
        return switch (op) {
            case CANCEL_ORDER -> new Text("Cancel this order?",
                    "The order will be cancelled" + (money == null ? "." : " and " + money
                            + " refunded to the original payment method.") + " This cannot be undone.",
                    "Yes, cancel order", "No, keep it");
            case START_RETURN -> new Text("Start a return for this item?",
                    "A return request will be opened. Once the item is received, up to " + money
                            + " is refunded to the original payment method.",
                    "Yes, start return", "Not now");
            case REQUEST_REFUND -> new Text("Issue this refund?",
                    money + " will be refunded to the original payment method. This cannot be undone.",
                    "Yes, refund " + money, "Don't refund");
            default -> throw new IllegalStateException(op.name());
        };
    }

    private ObjectNode storedParams(ActionType op, WriteParams p, String pending) {
        ObjectNode node = json.createObjectNode();
        node.put(ActionService.OP, op.name());
        node.put(ActionService.PENDING, pending);
        if (p.orderId() != null) node.put("orderId", p.orderId());
        if (p.orderItemId() != null) node.put("orderItemId", p.orderItemId());
        if (p.returnReason() != null) node.put("returnReason", p.returnReason().name());
        if (p.note() != null) node.put("note", p.note());
        if (p.amount() != null) node.put("amount", p.amount().toPlainString());
        if (p.reason() != null) node.put("reason", p.reason());
        return node;
    }

    private static WriteParams paramsOf(JsonNode n) {
        return new WriteParams(longOf(n, "orderId"), longOf(n, "orderItemId"),
                n.hasNonNull("returnReason") ? ReturnReason.valueOf(n.get("returnReason").asString()) : null,
                ActionService.text(n, "note"),
                n.hasNonNull("amount") ? new BigDecimal(n.get("amount").asString()) : null,
                ActionService.text(n, "reason"));
    }

    private static Long longOf(JsonNode n, String key) {
        return n.hasNonNull(key) ? n.get(key).asLong() : null;
    }

    // ------------------------------------------------------------------ commit / decline

    /** Runs the confirmed write. Any unusable token is {@link ActionNotFoundException}; nothing is written then. */
    public CommitResult commit(AgentContext ctx, String confirmToken) {
        AiAction action = tokens.resolve(confirmToken, ctx);
        if (action.getActionType() != ActionType.CONFIRM) {
            throw tokens.notFound(Reason.WRONG_KIND, ctx, action);
        }
        ActionType op = ActionType.valueOf(ActionService.text(action.getParams(), ActionService.OP));
        if (!actions.claimPair(action, ActionOutcome.CONFIRMED, ActionOutcome.CONFIRMED, "confirmed")) {
            throw tokens.notFound(Reason.LOST_RACE, ctx, action);
        }
        WriteParams p = paramsOf(action.getParams());
        String blockId = "action-" + action.getId();
        try {
            CommitResult result = switch (op) {
                case CANCEL_ORDER -> {
                    WriteOutcome<OrderDetailView> w = orders.cancelOrder(ctx, p.orderId());
                    yield new CommitResult(op, ActionOutcome.EXECUTED, w, blocks.orderDetail(blockId, w.state(), ctx));
                }
                case START_RETURN -> {
                    WriteOutcome<ReturnView> w = returns.createReturn(ctx, p.orderId(), p.orderItemId(),
                            p.returnReason(), p.note());
                    ReturnView r = w.state();
                    yield new CommitResult(op, ActionOutcome.EXECUTED, w, new ReturnStatusBlock(blockId, r.id(),
                            r.orderId(), r.orderNumber(), r.product(), r.qty(), r.status(), r.reason(), r.openedAt(),
                            r.resolvedAt(), null, List.of()));
                }
                case REQUEST_REFUND -> {
                    WriteOutcome<RefundView> w = refunds.issueRefund(ctx, p.orderId(), p.amount(), p.reason());
                    RefundView f = w.state();
                    yield new CommitResult(op, ActionOutcome.EXECUTED, w, new RefundResultBlock(blockId, f.id(),
                            f.orderId(), f.amount(), f.currency(), f.status(), f.method()));
                }
                default -> throw new IllegalStateException(op.name());
            };
            actions.recordOutcome(action.getId(), ActionOutcome.EXECUTED, result.write().message());
            return result;
        } catch (PortException e) {
            // the port re-checked its rules against the current state and refused: nothing was written
            String detail = e.code() + ": " + e.getMessage();
            actions.recordOutcome(action.getId(), ActionOutcome.REJECTED, detail);
            log.info("Confirmed {} rejected by the port: {}", op, detail);
            return new CommitResult(op, ActionOutcome.REJECTED, new WriteOutcome<>(false, null, e.getMessage()), null);
        } catch (RuntimeException e) {
            actions.recordOutcome(action.getId(), ActionOutcome.FAILED, "internal error: " + e.getClass().getSimpleName());
            log.error("Confirmed {} failed (action {})", op, action.getId(), e);
            return new CommitResult(op, ActionOutcome.FAILED,
                    new WriteOutcome<>(false, null, "The change could not be made; please try again later."), null);
        }
    }

    /** Declines a pending write: both tokens become USED with outcome DECLINED; nothing is written. */
    public DeclineResult decline(AgentContext ctx, String declineToken) {
        AiAction action = tokens.resolve(declineToken, ctx);
        if (action.getActionType() != ActionType.DECLINE) {
            throw tokens.notFound(Reason.WRONG_KIND, ctx, action);
        }
        ActionType op = ActionType.valueOf(ActionService.text(action.getParams(), ActionService.OP));
        if (!actions.claimPair(action, ActionOutcome.DECLINED, ActionOutcome.DECLINED, "declined")) {
            throw tokens.notFound(Reason.LOST_RACE, ctx, action);
        }
        return new DeclineResult(op, ActionOutcome.DECLINED, "Okay, nothing was changed.");
    }
}
