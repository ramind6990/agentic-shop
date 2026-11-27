package com.rmrx.agenticshop.ai2.contract.reply;

import com.rmrx.agenticshop.ai2.contract.tool.ToolResponse;
import com.rmrx.agenticshop.ai2.contract.tool.ToolResult;
import com.rmrx.agenticshop.ai2.contract.tool.response.order.OrderDetailResponse;
import com.rmrx.agenticshop.ai2.contract.tool.response.order.OrderItem;
import com.rmrx.agenticshop.ai2.contract.tool.response.order.OrderListResponse;
import com.rmrx.agenticshop.ai2.contract.tool.response.product.ProductDetailResponse;
import com.rmrx.agenticshop.ai2.contract.tool.response.product.ProductItem;
import com.rmrx.agenticshop.ai2.contract.tool.response.product.ProductListResponse;
import com.rmrx.agenticshop.ai2.graph.agent.response.AgentResponse;
import com.rmrx.agenticshop.ai2.graph.agent.response.DisplayReference;

import java.util.List;

/**
 * Maps the agent's response to a {@link ReplyEnvelope}. {@code displayed} is the tool result named by
 * {@code finalReferenceToDisplay.ref}; product and order results become a block (a single product or order shows as a
 * one-card list until it has its own block), narrowed to the {@code selectedItemIdentifiers} (empty = all; same ids as
 * {@code ToolResponse.itemIdentifiers()}). Any other result leaves the reply text only.
 */
public final class ReplyEnvelopes {

    private ReplyEnvelopes() {
    }

    /** Whether a tool result can be shown as a block (products and orders). */
    public static boolean displayable(ToolResponse value) {
        return value instanceof ProductListResponse || value instanceof ProductDetailResponse
                || value instanceof OrderListResponse || value instanceof OrderDetailResponse;
    }

    public static ReplyEnvelope of(AgentResponse response, ToolResult displayed) {
        List<String> selected = selected(response.finalReferenceToDisplay());
        return switch (displayed == null ? null : displayed.value()) {
            case ProductListResponse products -> products(response, select(products.items(), selected));
            case ProductDetailResponse product -> products(response, List.of(product.card()));
            case OrderListResponse orders -> orders(response, select(orders.items(), selected));
            case OrderDetailResponse order -> orders(response, List.of(order.card()));
            case null, default -> envelope(response, BlockType.NONE, null, null);
        };
    }

    private static ReplyEnvelope products(AgentResponse response, List<ProductItem> items) {
        return envelope(response, BlockType.PRODUCT_LIST, items, null);
    }

    private static ReplyEnvelope orders(AgentResponse response, List<OrderItem> items) {
        return envelope(response, BlockType.ORDER_LIST, null, items);
    }

    private static ReplyEnvelope envelope(AgentResponse response, BlockType type, List<ProductItem> products,
                                          List<OrderItem> orders) {
        return new ReplyEnvelope(response.status(), response.text(), type, products, orders,
                response.suggestedReplies() == null ? List.of() : response.suggestedReplies());
    }

    private static List<String> selected(DisplayReference reference) {
        return reference == null || reference.selectedItemIdentifiers() == null
                ? List.of() : reference.selectedItemIdentifiers();
    }

    private static <T> List<T> select(List<T> items, List<String> selected) {
        return selected.isEmpty() ? items : items.stream().filter(i -> selected.contains(idOf(i))).toList();
    }

    private static String idOf(Object item) {
        return switch (item) {
            case ProductItem p -> String.valueOf(p.id());
            case OrderItem o -> o.orderNumber();
            default -> throw new IllegalArgumentException("not a card item: " + item);
        };
    }
}
