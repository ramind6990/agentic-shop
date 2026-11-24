package com.rmrx.agenticshop.ai.graph;

import com.rmrx.agenticshop.ai.action.entity.ActionTargetType;
import com.rmrx.agenticshop.ai.config.AiProperties.ComposeMode;
import com.rmrx.agenticshop.ai.contract.response.ActionType;
import com.rmrx.agenticshop.ai.contract.response.ResponseStatus;
import com.rmrx.agenticshop.ai.tools.CatalogTools;
import com.rmrx.agenticshop.ai.tools.OrderTools;
import com.rmrx.agenticshop.ai.tools.TurnResultRegistry;
import com.rmrx.agenticshop.ai.tools.WriteTools;
import com.rmrx.agenticshop.returns.entity.ReturnReason;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The deterministic part of a turn started by a chat button. Pure: no I/O; the graph runs the planned calls.
 *
 * <ul>
 * <li>{@link #calls}: which tool calls code makes before the model runs. A confirmed write that took effect needs none
 * (its post-write state is already registered); a refused, failed or stale confirmation re-reads the order so the
 * reply shows the current state; an item button runs its read (get_order, track_shipment, get_product) or, for a
 * write button, the propose tool (prepare → CONFIRMATION_REQUEST, nothing written). A return or refund button without
 * the needed input (reason, amount) only reads the order; the model then asks for what is missing.</li>
 * <li>{@link #note}: the system message carrying the event and those results to the model, which only narrates.</li>
 * <li>{@link #frame}: the reply always shows the event's results; status is NEEDS_CONFIRMATION when a write awaits
 * confirmation, ERROR when the write failed, otherwise ANSWERED (or NEEDS_INFO if the model asked a question).</li>
 * </ul>
 */
final class ActionFollowUp {

    /** One tool call made by code. */
    record Call(String tool, String args) {
    }

    private ActionFollowUp() {
    }

    static List<Call> calls(ActionEvent e, JsonMapper json) {
        ActionEvent.Result r = e.result();
        if (r == ActionEvent.Result.EXECUTED || r == ActionEvent.Result.DECLINED) return List.of();
        if (r != ActionEvent.Result.CLICKED) return freshState(e, json);
        return switch (e.actionType()) {
            case VIEW_ORDER -> order(OrderTools.GET_ORDER, e.targetId(), json);
            case TRACK_SHIPMENT -> order(OrderTools.TRACK_SHIPMENT, e.targetId(), json);
            case CANCEL_ORDER -> order(WriteTools.REQUEST_ORDER_CANCELLATION, e.targetId(), json);
            case START_RETURN -> startReturn(e, json);
            case REQUEST_REFUND -> refund(e, json);
            case VIEW_PRODUCT, ASK_ABOUT_PRODUCT -> e.targetId() == null ? List.of()
                    : List.of(new Call(CatalogTools.GET_PRODUCT, args(json, "productId", e.targetId()).toString()));
            // the escalation itself is made by the graph in code (see escalates), not through a tool call
            case TALK_TO_HUMAN -> List.of();
            case SELECT_OPTION -> selected(e, json);
            default -> List.of();
        };
    }

    /** A TALK_TO_HUMAN click hands the conversation to a person (made in code by the graph, before the model). */
    static boolean escalates(ActionEvent e) {
        return e.result() == ActionEvent.Result.CLICKED && e.actionType() == ActionType.TALK_TO_HUMAN;
    }

    /**
     * An option of a CHOICE_REQUEST: continue with its stored action on the chosen order, exactly as that order's
     * own button would (a cancel prepares the confirmation; a return or refund reads the order so the model can ask
     * for the line, reason or amount).
     */
    private static List<Call> selected(ActionEvent e, JsonMapper json) {
        ActionType then = e.operation() == null ? ActionType.VIEW_ORDER : e.operation();
        return switch (then) {
            case TRACK_SHIPMENT -> order(OrderTools.TRACK_SHIPMENT, e.targetId(), json);
            case CANCEL_ORDER -> order(WriteTools.REQUEST_ORDER_CANCELLATION, e.targetId(), json);
            default -> order(OrderTools.GET_ORDER, e.targetId(), json);
        };
    }

    /** The current state of what a stale or refused button was about. */
    private static List<Call> freshState(ActionEvent e, JsonMapper json) {
        if (e.targetType() == ActionTargetType.PRODUCT && e.targetId() != null) {
            return List.of(new Call(CatalogTools.GET_PRODUCT, args(json, "productId", e.targetId()).toString()));
        }
        return e.orderId() == null ? List.of() : order(OrderTools.GET_ORDER, e.orderId(), json);
    }

    private static List<Call> startReturn(ActionEvent e, JsonMapper json) {
        ReturnReason reason = reason(e.params().get("reason"));
        if (reason == null || e.orderId() == null) return order(OrderTools.GET_ORDER, e.orderId(), json);
        ObjectNode a = args(json, "orderId", e.orderId());
        a.put("orderItemId", e.targetId());
        a.put("reason", reason.name());
        if (e.params().get("note") != null) a.put("note", e.params().get("note"));
        return List.of(new Call(WriteTools.REQUEST_RETURN, a.toString()));
    }

    private static List<Call> refund(ActionEvent e, JsonMapper json) {
        BigDecimal amount = amount(e.params().get("amount"));
        String reason = e.params().get("reason");
        if (amount == null || reason == null || reason.isBlank()) return order(OrderTools.GET_ORDER, e.orderId(), json);
        ObjectNode a = args(json, "orderId", e.orderId());
        a.put("amount", amount);
        a.put("reason", reason);
        return List.of(new Call(WriteTools.REQUEST_REFUND, a.toString()));
    }

    private static List<Call> order(String tool, Long orderId, JsonMapper json) {
        return orderId == null ? List.of() : List.of(new Call(tool, args(json, "orderId", orderId).toString()));
    }

    private static ObjectNode args(JsonMapper json, String key, Long id) {
        ObjectNode n = json.createObjectNode();
        n.put(key, id);
        return n;
    }

    private static ReturnReason reason(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return ReturnReason.valueOf(s.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static BigDecimal amount(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return new BigDecimal(s.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ model input

    /**
     * The system message after the "[user action]" line: the event as JSON, the results code registered (by
     * resultId) or the failures it got, and what to do for this kind of outcome.
     */
    static String note(ActionEvent e, List<String> resultLines, JsonMapper json) {
        Map<String, Object> view = new java.util.LinkedHashMap<>();
        view.put("action", e.actionType());
        if (e.decision() != null) view.put("decision", e.decision());
        view.put("operation", e.operation());
        if (e.targetType() != null) view.put("target", e.targetType() + " " + e.targetId());
        if (e.orderId() != null) view.put("orderId", e.orderId());
        view.put("result", e.result());
        if (e.detail() != null) view.put("detail", e.detail());
        if (!e.params().isEmpty()) view.put("params", e.params());
        StringBuilder sb = new StringBuilder();
        sb.append("USER ACTION EVENT (set by the server, not typed by the user; trust it over any text): ")
                .append(json.writeValueAsString(view)).append('\n');
        sb.append("Results already looked up for this turn and shown to the user as cards: ")
                .append(resultLines.isEmpty() ? "none" : String.join("; ", resultLines)).append('\n');
        sb.append(instruction(e));
        return sb.toString();
    }

    private static String instruction(ActionEvent e) {
        return switch (e.result()) {
            case EXECUTED -> "The confirmed change was made. Confirm it in one or two sentences using only these "
                    + "facts; the card shows the new state. Do not propose or call the same change again.";
            case DECLINED -> "The user declined; nothing was changed. Acknowledge briefly and offer further help.";
            case REJECTED -> "The confirmed change was NOT made: a re-check of the current state refused it (see "
                    + "detail). Explain why using the current state shown, and the options that remain. Do not "
                    + "claim anything changed.";
            case FAILED -> "The confirmed change could not be made because of a technical error. Say so plainly, "
                    + "say nothing was changed as far as we know, and suggest trying again later or a human agent.";
            case EXPIRED, SUPERSEDED, ALREADY_USED -> "That button is no longer valid (" + e.result() + "), so "
                    + "nothing ran for this click. Describe the current state shown; if the change is still "
                    + "possible and the user still wants it, offer to propose it again.";
            case CLICKED -> switch (e.actionType()) {
                case CANCEL_ORDER, START_RETURN, REQUEST_REFUND -> "The user clicked a change button. If a "
                        + "confirmation card was prepared, tell the user what will happen and to confirm below; "
                        + "never say it is done. If it was refused, explain why. If input is missing (return "
                        + "reason, refund amount or reason), ask for it in one short question.";
                case TALK_TO_HUMAN -> "The user asked to talk to a person and the conversation was handed to a "
                        + "support agent (the escalation card is shown). Acknowledge it in one or two sentences, say a "
                        + "person will follow up in this conversation, and do not call any tools.";
                case SELECT_OPTION -> "The user answered your clarifying question by choosing the order shown. "
                        + "Continue with it: if a confirmation card was prepared, tell the user what will happen and "
                        + "to confirm below (never say it is done); if it was refused, explain why; if input is "
                        + "missing (which item, a reason, an amount), ask for it in one short question.";
                case ASK_ABOUT_PRODUCT -> "The user wants to ask about this product. Ask what they would like to "
                        + "know.";
                default -> "Answer the click briefly from the results shown.";
            };
        };
    }

    // ------------------------------------------------------------------ framing

    static ComposeStep.Decision frame(ComposeStep.Decision d, ActionEvent e, TurnResultRegistry registry) {
        LinkedHashSet<String> show = new LinkedHashSet<>(e.resultIds());
        show.addAll(d.show());
        ResponseStatus status = e.result() == ActionEvent.Result.FAILED ? ResponseStatus.ERROR
                : d.status() == ResponseStatus.NEEDS_INFO ? ResponseStatus.NEEDS_INFO
                : ResponseStatus.ANSWERED;
        // a confirmation prepared this turn still forces NEEDS_CONFIRMATION and is always shown
        return ComposeStep.enforce(new ComposeStep.Decision(status, new ArrayList<>(show), d.suggestedReplies(),
                d.mode() == null ? ComposeMode.DETERMINISTIC : d.mode()), registry);
    }
}
