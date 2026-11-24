package com.rmrx.agenticshop.ai.tools.port;

import com.rmrx.agenticshop.ai.context.AgentContext;
import com.rmrx.agenticshop.ai.contract.tool.ProductDetailView;
import com.rmrx.agenticshop.ai.contract.tool.ProductSummaryView;

import java.math.BigDecimal;

/** The public catalog; every role sees the same products. */
public interface CatalogPort {

    /** All filters optional; category matches the category slug or any parent (e.g. "audio", "electronics"). */
    Page<ProductSummaryView> search(AgentContext ctx, String query, String category, BigDecimal maxPrice, int page,
                                    int size);

    ProductDetailView getProduct(AgentContext ctx, Long productId);
}
