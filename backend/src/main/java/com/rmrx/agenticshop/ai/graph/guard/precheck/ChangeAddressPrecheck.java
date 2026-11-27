package com.rmrx.agenticshop.ai.graph.guard.precheck;

import com.rmrx.agenticshop.ai.tools.order.OrderTools;
import com.rmrx.agenticshop.app.order.dto.OrderDtos.Verdict;
import com.rmrx.agenticshop.app.order.service.OrderCommandService;
import org.springframework.stereotype.Component;

/** {@code update_shipping_address}: an order not shipped yet, and a saved address of the order's customer. */
@Component
class ChangeAddressPrecheck implements SensitivePrecheck {

    private final OrderCommandService orders;

    ChangeAddressPrecheck(OrderCommandService orders) {
        this.orders = orders;
    }

    @Override
    public String toolName() {
        return OrderTools.UPDATE_SHIPPING_ADDRESS;
    }

    @Override
    public PrecheckResult check(PrecheckArgs args) {
        Verdict verdict = orders.checkChangeAddress(args.text("orderNumber"), args.id("addressId"));
        return verdict.allowed() ? PrecheckResult.allowed(null) : PrecheckResult.blocked(verdict.reason());
    }
}
