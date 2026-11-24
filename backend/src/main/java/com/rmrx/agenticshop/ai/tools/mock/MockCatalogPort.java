package com.rmrx.agenticshop.ai.tools.mock;

import com.rmrx.agenticshop.ai.context.AgentContext;
import com.rmrx.agenticshop.ai.contract.tool.ProductDetailView;
import com.rmrx.agenticshop.ai.contract.tool.ProductSummaryView;
import com.rmrx.agenticshop.ai.tools.port.CatalogPort;
import com.rmrx.agenticshop.ai.tools.port.Page;
import com.rmrx.agenticshop.ai.tools.port.PortException;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * {@link CatalogPort} over the caller's {@link MockWorld}: every query word must appear in the title, brand or
 * category path; results are ordered by number of ratings.
 */
public class MockCatalogPort extends MockSupport implements CatalogPort {

    public MockCatalogPort(MockWorlds worlds) {
        super(worlds);
    }

    @Override
    public Page<ProductSummaryView> search(AgentContext ctx, String query, String category, BigDecimal maxPrice,
                                           int page, int size) {
        int pageSize = Page.checkedSize(page, size);
        if (maxPrice != null && maxPrice.signum() <= 0) throw PortException.invalid("maxPrice must be positive");
        List<String> words = query == null ? List.of()
                : Arrays.stream(query.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                  .filter(w -> !w.isBlank()).toList();
        String cat = category == null || category.isBlank() ? null : category.trim().toLowerCase(Locale.ROOT);
        return inWorld(ctx, world -> {
            List<ProductSummaryView> hits = world.products.values().stream()
                    .filter(p -> cat == null || MockScenarios.categoryChain(p.category).contains(cat))
                    .filter(p -> maxPrice == null || p.price.compareTo(maxPrice) <= 0)
                    .filter(p -> {
                        String text = (p.title + " " + p.brand + " "
                                + String.join(" ", MockScenarios.categoryChain(p.category)))
                                .toLowerCase(Locale.ROOT);
                        return words.stream().allMatch(text::contains);
                    })
                    .sorted(Comparator.comparingInt((MockWorld.Product p) -> p.ratingCount).reversed())
                    .map(MockSupport::productSummary)
                    .toList();
            return Page.of(hits, page, pageSize);
        });
    }

    @Override
    public ProductDetailView getProduct(AgentContext ctx, Long productId) {
        requireId("productId", productId);
        return inWorld(ctx, world -> {
            MockWorld.Product p = world.products.get(productId);
            if (p == null) throw PortException.notFound("product", productId);
            return productDetail(p);
        });
    }
}
