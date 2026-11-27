package com.rmrx.agenticshop.ai2.contract.reply;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rmrx.agenticshop.ai2.contract.tool.response.order.OrderItem;
import com.rmrx.agenticshop.ai2.contract.tool.response.product.ProductItem;
import com.rmrx.agenticshop.ai2.graph.agent.response.ResponseStatus;
import com.rmrx.agenticshop.ai2.graph.agent.response.SuggestionReply;

import java.util.List;

/**
 * The reply as the UI renders it: the agent's text, at most one list of items (named by {@link #blockType()}; only that
 * list is set) and the quick replies. Built by {@link ReplyEnvelopes} from the agent's response and the tool result it
 * chose to display.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReplyEnvelope(ResponseStatus status, String text, BlockType blockType, List<ProductItem> productList,
                            List<OrderItem> orderList, List<SuggestionReply> suggestedReplies) {
}
