package com.rmrx.agenticshop.ai2.contract.reply;

import com.rmrx.agenticshop.ai2.contract.tool.ToolResult;
import com.rmrx.agenticshop.ai2.contract.tool.response.OrderItem;
import com.rmrx.agenticshop.ai2.contract.tool.response.OrderListResponse;
import com.rmrx.agenticshop.ai2.contract.tool.response.ProductItem;
import com.rmrx.agenticshop.ai2.contract.tool.response.ProductListResponse;
import com.rmrx.agenticshop.ai2.graph.agent.response.AgentResponse;
import com.rmrx.agenticshop.ai2.graph.agent.response.DisplayReference;

import java.util.List;
import java.util.function.Function;

/**
 * Maps the agent's response to a {@link ReplyEnvelope}. {@code displayed} is the tool result named by
 * {@code finalReferenceToDisplay.ref}; only list results become a block, narrowed to the
 * {@code selectedItemIdentifiers} (empty = all; same ids as {@code ToolResponse.itemIdentifiers()}). Any other result
 * (e.g. an order cancellation) leaves the reply text only.
 */
public final class ReplyEnvelopes {

    private ReplyEnvelopes() {
    }

    public static ReplyEnvelope of(AgentResponse response, ToolResult displayed) {
        List<String> selected = selected(response.finalReferenceToDisplay());
        return switch (displayed == null ? null : displayed.value()) {
            case ProductListResponse products -> envelope(response, BlockType.PRODUCT_LIST,
                    select(products.items(), p -> String.valueOf(p.id()), selected), null);
            case OrderListResponse orders -> envelope(response, BlockType.ORDER_LIST, null,
                    select(orders.items(), OrderItem::orderNumber, selected));
            case null, default -> envelope(response, BlockType.NONE, null, null);
        };
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

    private static <T> List<T> select(List<T> items, Function<T, String> id, List<String> selected) {
        return selected.isEmpty() ? items : items.stream().filter(i -> selected.contains(id.apply(i))).toList();
    }
}
