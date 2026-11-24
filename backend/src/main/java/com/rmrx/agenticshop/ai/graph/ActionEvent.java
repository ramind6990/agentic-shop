package com.rmrx.agenticshop.ai.graph;

import com.rmrx.agenticshop.ai.action.entity.ActionTargetType;
import com.rmrx.agenticshop.ai.contract.request.ActionRequest.Decision;
import com.rmrx.agenticshop.ai.contract.response.ActionType;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * What the user did with a chat button and what came of it, as structured data (never free text). It is resolved on
 * the request thread (a confirmed write has already run, or was refused, before the turn starts), stored as the
 * payload of the ACTION ticket message and handed to the graph in {@link AgentState#ACTION_EVENT}.
 *
 * @param actionId   the used {@code ai_action} row (never the token)
 * @param actionType the button: CONFIRM/DECLINE of a pending write, or an item action (TRACK_SHIPMENT, CANCEL_ORDER…)
 * @param decision   CONFIRM/DECLINE for confirmation buttons, null for item buttons
 * @param operation  the write a confirmation belongs to (CANCEL_ORDER, START_RETURN, REQUEST_REFUND); for an item
 *                   button its own type; for SELECT_OPTION the action the chosen option continues with
 * @param orderId    the order concerned, if any (target of ORDER buttons, stored param of item/confirmation buttons)
 * @param result     what happened; see {@link Result}
 * @param detail     short server-side detail (the port's message or refusal reason); never shown as model output
 * @param resultIds  results registered in the turn's registry by code for this event (the post-write state, a
 *                   fresh read, a prepared confirmation); the reply always shows them
 * @param params     extra input sent with the click (e.g. a return reason); never ids or amounts for a confirm
 */
public record ActionEvent(Long actionId, ActionType actionType, Decision decision, ActionType operation,
                          ActionTargetType targetType, Long targetId, Long orderId, Result result, String detail,
                          List<String> resultIds, Map<String, String> params) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Outcome of using the button. */
    public enum Result {
        /** The confirmed write took effect. */
        EXECUTED,
        /** The user declined the pending write; nothing changed. */
        DECLINED,
        /** The port re-checked the current state and refused (e.g. the order shipped meanwhile); nothing changed. */
        REJECTED,
        /** The write failed unexpectedly; nothing is known to have changed. */
        FAILED,
        /** The button was past its expiry; nothing ran. */
        EXPIRED,
        /** A newer proposal replaced this one; nothing ran. */
        SUPERSEDED,
        /** The button was already used (a replay); nothing ran again. */
        ALREADY_USED,
        /** An item button was used; it leads to a read or a new confirmation step, never to a write. */
        CLICKED;

        public boolean stale() {
            return this == EXPIRED || this == SUPERSEDED || this == ALREADY_USED;
        }
    }

    public ActionEvent {
        resultIds = resultIds == null ? List.of() : List.copyOf(resultIds);
        params = params == null ? Map.of() : Map.copyOf(params);
    }

    public ActionEvent withResultIds(List<String> ids) {
        return new ActionEvent(actionId, actionType, decision, operation, targetType, targetId, orderId, result,
                detail, ids, params);
    }

    public ActionEvent withResult(Result result, String detail) {
        return new ActionEvent(actionId, actionType, decision, operation, targetType, targetId, orderId, result,
                detail, resultIds, params);
    }

    public boolean confirmation() {
        return decision != null;
    }

    /** The readable line stored as the ACTION message: "Confirmed: cancel order #50001". */
    public String messageText() {
        if (decision == Decision.CONFIRM) return "Confirmed: " + subject();
        if (decision == Decision.DECLINE) return "Declined: " + subject();
        if (actionType == ActionType.SELECT_OPTION) return "Selected: " + subject();
        return "Clicked: " + subject();
    }

    /** What the button was about, e.g. "cancel order #50001" or "Track shipment for order #50003". */
    public String subject() {
        String order = orderId == null ? "" : "order #" + orderId;
        if (confirmation()) {
            return switch (operation) {
                case CANCEL_ORDER -> "cancel " + order;
                case START_RETURN -> "return item " + targetId + " of " + order;
                case REQUEST_REFUND -> "refund" + (params.containsKey("amount") ? " " + params.get("amount") : "")
                        + " on " + order;
                default -> operation.name().toLowerCase() + " " + order;
            };
        }
        return switch (actionType) {
            case VIEW_ORDER -> "View " + order;
            case TRACK_SHIPMENT -> "Track shipment for " + order;
            case CANCEL_ORDER -> "Cancel " + order;
            case START_RETURN -> "Return item " + targetId + " of " + order;
            case REQUEST_REFUND -> "Issue refund on " + order;
            case VIEW_PRODUCT -> "View product " + targetId;
            case ASK_ABOUT_PRODUCT -> "Ask about product " + targetId;
            case TALK_TO_HUMAN -> "Talk to a person";
            case SELECT_OPTION -> order + (operation == null || operation == ActionType.SELECT_OPTION ? ""
                    : " (to " + operation.name().toLowerCase().replace('_', ' ') + ")");
            default -> actionType.name() + (targetId == null ? "" : " " + targetId);
        };
    }

    /** The line the model sees for this event, now and in later turns' history. */
    public String historyLine() {
        return historyLine(messageText(), result == null ? null : result.name(), detail);
    }

    /** "[user action] Confirmed: cancel order #50001 → EXECUTED (Order AS-50001 cancelled.)". */
    public static String historyLine(String text, String result, String detail) {
        StringBuilder sb = new StringBuilder("[user action] ").append(text == null ? "(unknown)" : text);
        if (result != null) sb.append(" → ").append(result);
        if (detail != null && !detail.isBlank()) sb.append(" (").append(clip(detail, 200)).append(')');
        return sb.toString();
    }

    private static String clip(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
