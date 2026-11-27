package com.rmrx.agenticshop.ai.contract.reply.block;

import com.rmrx.agenticshop.ai.contract.tool.ToolResponse;
import com.rmrx.agenticshop.ai.contract.tool.response.customer.AddressListResponse;
import com.rmrx.agenticshop.ai.contract.tool.response.customer.CustomerDetailResponse;
import com.rmrx.agenticshop.ai.contract.tool.response.customer.CustomerListResponse;
import com.rmrx.agenticshop.ai.contract.tool.response.order.OrderCancellationResponse;
import com.rmrx.agenticshop.ai.contract.tool.response.order.OrderDetailResponse;
import com.rmrx.agenticshop.ai.contract.tool.response.order.OrderItem;
import com.rmrx.agenticshop.ai.contract.tool.response.order.OrderListResponse;
import com.rmrx.agenticshop.ai.contract.tool.response.order.ShippingAddressChangeResponse;
import com.rmrx.agenticshop.ai.contract.tool.response.product.ProductDetailResponse;
import com.rmrx.agenticshop.ai.contract.tool.response.product.ProductListResponse;
import com.rmrx.agenticshop.ai.contract.tool.response.product.ReviewListResponse;
import com.rmrx.agenticshop.ai.contract.tool.response.returns.RefundListResponse;
import com.rmrx.agenticshop.ai.contract.tool.response.returns.RefundResponse;
import com.rmrx.agenticshop.ai.contract.tool.response.returns.ReturnListResponse;
import com.rmrx.agenticshop.ai.contract.tool.response.returns.ReturnResponse;
import com.rmrx.agenticshop.ai.contract.tool.response.shipment.ShipmentListResponse;
import com.rmrx.agenticshop.ai.contract.tool.response.shipment.TrackingResponse;
import com.rmrx.agenticshop.ai.contract.tool.response.support.TicketActionResponse;
import com.rmrx.agenticshop.ai.contract.tool.response.support.TicketDetailResponse;
import com.rmrx.agenticshop.ai.contract.tool.response.support.TicketItem;
import com.rmrx.agenticshop.ai.contract.tool.response.support.TicketListResponse;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Builds the {@link ReplyBlock} of a displayed tool result. List results are narrowed to the selected item ids (the
 * same ids as {@code ToolResponse.itemIdentifiers()}; empty = all); a single changed entity (a return, refund or ticket
 * after an action) shows as a one-item list. Only category filters have no block: they help the agent, not the user.
 */
public final class ReplyBlocks {

    private ReplyBlocks() {
    }

    /** Whether a tool result can be shown as a block. */
    public static boolean displayable(ToolResponse value) {
        return of(value, List.of()).isPresent();
    }

    public static Optional<ReplyBlock> of(ToolResponse value, List<String> selected) {
        return Optional.ofNullable(switch (value) {
            case ProductListResponse r -> new ProductListBlock(select(r.items(), selected, p -> String.valueOf(p.id())));
            case ProductDetailResponse r -> new ProductDetailBlock(r);
            case ReviewListResponse r ->
                    new ReviewListBlock(r.productId(), select(r.items(), selected, v -> String.valueOf(v.id())));
            case OrderListResponse r -> new OrderListBlock(select(r.items(), selected, OrderItem::orderNumber));
            case OrderDetailResponse r -> new OrderDetailBlock(r);
            case OrderCancellationResponse r -> new OrderCancellationBlock(r);
            case ShippingAddressChangeResponse r -> new AddressChangeBlock(r);
            case TrackingResponse r -> new TrackingBlock(new TrackingResponse(r.orderNumber(), r.orderStatus(),
                    select(r.shipments(), selected, TrackingResponse.Shipment::trackingNumber)));
            case ShipmentListResponse r ->
                    new ShipmentListBlock(select(r.items(), selected, ShipmentListResponse.Row::trackingNumber));
            case ReturnListResponse r -> new ReturnListBlock(select(r.items(), selected, i -> String.valueOf(i.id())));
            case ReturnResponse r -> new ReturnListBlock(List.of(r.returnRequest()));
            case RefundListResponse r -> new RefundListBlock(select(r.items(), selected, i -> String.valueOf(i.id())));
            case RefundResponse r -> new RefundListBlock(List.of(r.refund()));
            case TicketListResponse r -> new TicketListBlock(select(r.items(), selected, TicketItem::ticketNumber));
            case TicketActionResponse r -> new TicketListBlock(List.of(r.ticket()));
            case TicketDetailResponse r -> new TicketDetailBlock(r);
            case CustomerListResponse r ->
                    new CustomerListBlock(select(r.items(), selected, c -> String.valueOf(c.id())));
            case CustomerDetailResponse r -> new CustomerDetailBlock(r);
            case AddressListResponse r ->
                    new AddressListBlock(select(r.addresses(), selected, a -> String.valueOf(a.id())));
            case null, default -> null;
        });
    }

    private static <T> List<T> select(List<T> items, List<String> selected, Function<T, String> id) {
        return selected.isEmpty() ? items : items.stream().filter(i -> selected.contains(id.apply(i))).toList();
    }
}
