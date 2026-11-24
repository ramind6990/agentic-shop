package com.rmrx.agenticshop.ai.tools;

import com.rmrx.agenticshop.ai.contract.tool.OrderDetailView;
import com.rmrx.agenticshop.ai.contract.tool.OrderSummaryView;
import com.rmrx.agenticshop.ai.contract.tool.ProductDetailView;
import com.rmrx.agenticshop.ai.contract.tool.ProductSummaryView;
import com.rmrx.agenticshop.ai.contract.tool.RefundView;
import com.rmrx.agenticshop.ai.contract.tool.ReturnView;
import com.rmrx.agenticshop.ai.contract.tool.ShipmentView;
import com.rmrx.agenticshop.ai.contract.tool.TicketMessageView;
import com.rmrx.agenticshop.ai.contract.tool.WriteOutcome;
import com.rmrx.agenticshop.ai.tools.port.Page;
import com.rmrx.agenticshop.ai.tools.port.TicketView;

import java.util.Collection;

/**
 * The tools' own check of what a port returned, so a broken upstream becomes UNAVAILABLE instead of nonsense reaching
 * the model: required fields present, lists not null and without null elements, nested views valid too.
 */
final class ResultValidator {

    static final class MalformedResultException extends RuntimeException {
        MalformedResultException(String what) {
            super("malformed upstream response: " + what, null, false, false);
        }
    }

    private ResultValidator() {
    }

    static void check(Object value) {
        switch (value) {
            case null -> throw new MalformedResultException("null");
            case Collection<?> c -> c.forEach(ResultValidator::check);
            case Page<?> p -> {
                require(p.items(), "page.items");
                check(p.items());
            }
            case OrderSummaryView o -> {
                require(o.id(), "order.id");
                require(o.number(), "order.number");
                require(o.status(), "order.status");
            }
            case OrderDetailView o -> {
                require(o.id(), "order.id");
                require(o.number(), "order.number");
                require(o.status(), "order.status");
                require(o.total(), "order.total");
                o.lines().forEach(l -> require(l.itemId(), "line.itemId"));
                check(o.shipments());
            }
            case ShipmentView s -> {
                require(s.id(), "shipment.id");
                require(s.orderId(), "shipment.orderId");
                require(s.status(), "shipment.status");
            }
            case ProductSummaryView p -> {
                require(p.id(), "product.id");
                require(p.title(), "product.title");
                require(p.price(), "product.price");
            }
            case ProductDetailView p -> {
                require(p.id(), "product.id");
                require(p.title(), "product.title");
                require(p.price(), "product.price");
            }
            case ReturnView r -> {
                require(r.id(), "return.id");
                require(r.orderId(), "return.orderId");
                require(r.status(), "return.status");
            }
            case RefundView r -> {
                require(r.id(), "refund.id");
                require(r.amount(), "refund.amount");
                require(r.status(), "refund.status");
            }
            case TicketMessageView m -> {
                require(m.id(), "message.id");
                require(m.authorType(), "message.authorType");
                require(m.body(), "message.body");
            }
            case TicketView t -> {
                require(t.id(), "ticket.id");
                require(t.status(), "ticket.status");
            }
            case WriteOutcome<?> w -> {
                if (w.success()) check(w.state());
            }
            case ToolData.Tracking t -> check(t.shipments());
            case ToolData.ReturnStatus r -> {
                check(r.returns());
                check(r.refunds());
            }
            case ToolData.TicketHistory h -> check(h.messages());
            default -> {
            }
        }
    }

    private static void require(Object field, String what) {
        if (field == null) throw new MalformedResultException(what + " missing");
    }
}
