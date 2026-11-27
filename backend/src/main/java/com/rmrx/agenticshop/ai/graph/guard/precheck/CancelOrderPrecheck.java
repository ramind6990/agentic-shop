package com.rmrx.agenticshop.ai.graph.guard.precheck;

import com.rmrx.agenticshop.ai.tools.order.OrderTools;
import com.rmrx.agenticshop.app.order.dto.OrderDtos.Verdict;
import com.rmrx.agenticshop.app.order.service.OrderCommandService;
import org.springframework.stereotype.Component;

/** {@code cancel_order}: only an order that has not been shipped yet. */
@Component
class CancelOrderPrecheck implements SensitivePrecheck {

    private final OrderCommandService orders;

    CancelOrderPrecheck(OrderCommandService orders) {
        this.orders = orders;
    }

    @Override
    public String toolName() {
        return OrderTools.CANCEL_ORDER;
    }

    @Override
    public PrecheckResult check(PrecheckArgs args) {
        Verdict verdict = orders.checkCancel(args.text("orderNumber"));
        return verdict.allowed() ? PrecheckResult.allowed(null) : PrecheckResult.blocked(verdict.reason());
    }
}
