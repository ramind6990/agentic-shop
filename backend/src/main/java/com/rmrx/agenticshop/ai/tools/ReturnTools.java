package com.rmrx.agenticshop.ai.tools;

import com.rmrx.agenticshop.ai.context.AgentContext;
import com.rmrx.agenticshop.ai.contract.tool.RefundView;
import com.rmrx.agenticshop.ai.contract.tool.ReturnView;
import com.rmrx.agenticshop.ai.tools.port.PortException;
import com.rmrx.agenticshop.ai.tools.port.RefundPort;
import com.rmrx.agenticshop.ai.tools.port.ReturnPort;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/** Read tools over returns and refunds. */
public class ReturnTools extends AbstractTools {

    public static final String GET_RETURN_STATUS = "get_return_status";

    private final ReturnPort returns;
    private final RefundPort refunds;

    ReturnTools(AgentContext ctx, TurnResultRegistry registry, JsonMapper json, ReturnPort returns,
                RefundPort refunds) {
        super(ctx, registry, json);
        this.returns = returns;
        this.refunds = refunds;
    }

    @Tool(name = GET_RETURN_STATUS, value = "Gets the returns and refunds of an order: pass exactly one of "
            + "returnId (a specific return; its order's refunds are included) or orderId (all returns and "
            + "refunds of that order).")
    @ToolLabel("Checking the return status\u2026")
    public String getReturnStatus(
            @P(value = "Numeric return id", required = false) Long returnId,
            @P(value = "Numeric order id", required = false) Long orderId) {
        return run(GET_RETURN_STATUS, () -> {
            if ((returnId == null) == (orderId == null)) {
                throw PortException.invalid("pass exactly one of returnId or orderId");
            }
            List<ReturnView> found;
            Long order;
            if (returnId != null) {
                requireId("returnId", returnId);
                ReturnView r = returns.getReturn(ctx, returnId);
                found = r == null ? null : List.of(r);
                order = r == null ? null : r.orderId();
            } else {
                requireId("orderId", orderId);
                found = returns.listReturnsForOrder(ctx, orderId);
                order = orderId;
            }
            ResultValidator.check(found);
            List<RefundView> refundList = refunds.listRefunds(ctx, order);
            ResultValidator.check(refundList);
            return new ToolData.ReturnStatus(order, cap(found), found.size(), cap(refundList), refundList.size(),
                    found.size() > PAGE_SIZE || refundList.size() > PAGE_SIZE);
        });
    }

    private static <T> List<T> cap(List<T> list) {
        return list.size() <= PAGE_SIZE ? list : list.subList(0, PAGE_SIZE);
    }
}
