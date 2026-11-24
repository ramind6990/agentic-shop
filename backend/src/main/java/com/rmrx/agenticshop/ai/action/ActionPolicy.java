package com.rmrx.agenticshop.ai.action;

import com.rmrx.agenticshop.ai.context.AgentContext;
import com.rmrx.agenticshop.ai.contract.response.ActionType;
import com.rmrx.agenticshop.ai.tools.port.OrderFacts;
import com.rmrx.agenticshop.ai.tools.port.WriteRules;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Which buttons an item of a reply gets, from the item's current state and the caller. Pure: no I/O, no tokens.
 * Business limits come from {@link WriteRules}, the same rules the ports enforce on the write itself, so a button is
 * never offered for something the port would refuse on rules it knows up front. Showing a button is a hint only: the
 * write still goes through confirmation and the port re-checks everything.
 */
@Component
public class ActionPolicy {

    /** One allowed button. requiresConfirmation: clicking leads to a confirmation step, not to the write. */
    public record Allowed(ActionType type, String label, boolean requiresConfirmation) {

        static Allowed of(ActionType type) {
            return new Allowed(type, ActionPolicy.label(type), confirms(type));
        }
    }

    /** VIEW_ORDER always; TRACK_SHIPMENT once shipped; CANCEL_ORDER if cancellable by the caller; staff refunds. */
    public List<Allowed> forOrder(OrderFacts order, AgentContext ctx) {
        List<Allowed> out = new ArrayList<>();
        out.add(Allowed.of(ActionType.VIEW_ORDER));
        if (order.shipmentCount() > 0) out.add(Allowed.of(ActionType.TRACK_SHIPMENT));
        if (WriteRules.CANCELLABLE.contains(order.status()) && mayChange(order, ctx)) {
            out.add(Allowed.of(ActionType.CANCEL_ORDER));
        }
        if (ctx.isStaff() && WriteRules.mayRefund(ctx.role()) && order.refundable() != null
                && order.refundable().signum() > 0) {
            out.add(Allowed.of(ActionType.REQUEST_REFUND));
        }
        return out;
    }

    /** START_RETURN for a delivered line inside the return window, without a blocking return, for owner or staff. */
    public List<Allowed> forOrderLine(OrderFacts order, OrderFacts.ItemFacts item, AgentContext ctx, Instant now) {
        boolean returnable = mayChange(order, ctx)
                && !WriteRules.NOT_RETURNABLE.contains(order.status())
                && item.deliveredAt() != null
                && WriteRules.withinReturnWindow(item.deliveredAt(), now)
                && !item.hasActiveReturn();
        return returnable ? List.of(Allowed.of(ActionType.START_RETURN)) : List.of();
    }

    public List<Allowed> forProduct(AgentContext ctx) {
        return List.of(Allowed.of(ActionType.VIEW_PRODUCT), Allowed.of(ActionType.ASK_ABOUT_PRODUCT));
    }

    public List<Allowed> forReturn(AgentContext ctx) {
        return List.of(Allowed.of(ActionType.VIEW_ORDER));
    }

    /** Reply-level suggestions, offered on every reply. */
    public List<Allowed> suggestions(AgentContext ctx) {
        return List.of(Allowed.of(ActionType.TALK_TO_HUMAN));
    }

    /** The owning customer or staff; sellers never change customer orders. */
    static boolean mayChange(OrderFacts order, AgentContext ctx) {
        if (ctx.isCustomer()) return Objects.equals(order.customerId(), ctx.userId());
        return ctx.isStaff();
    }

    static boolean confirms(ActionType type) {
        return switch (type) {
            case CANCEL_ORDER, START_RETURN, REQUEST_REFUND -> true;
            default -> false;
        };
    }

    public static String label(ActionType type) {
        return switch (type) {
            case VIEW_ORDER -> "View order";
            case TRACK_SHIPMENT -> "Track shipment";
            case CANCEL_ORDER -> "Cancel order";
            case START_RETURN -> "Return item";
            case REQUEST_REFUND -> "Issue refund";
            case VIEW_PRODUCT -> "View product";
            case ASK_ABOUT_PRODUCT -> "Ask about this product";
            case CONFIRM -> "Confirm";
            case DECLINE -> "Cancel";
            case SELECT_OPTION -> "Select";
            case TALK_TO_HUMAN -> "Talk to a person";
        };
    }
}
