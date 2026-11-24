package com.rmrx.agenticshop.ai.tools;

import com.rmrx.agenticshop.ai.context.AgentContext;
import com.rmrx.agenticshop.ai.tools.port.CatalogPort;
import com.rmrx.agenticshop.ai.tools.port.PortException;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;

/** Read tools over the public catalog. */
public class CatalogTools extends AbstractTools {

    public static final String SEARCH_PRODUCTS = "search_products";
    public static final String GET_PRODUCT = "get_product";

    private final CatalogPort catalog;

    CatalogTools(AgentContext ctx, TurnResultRegistry registry, JsonMapper json, CatalogPort catalog) {
        super(ctx, registry, json);
        this.catalog = catalog;
    }

    @Tool(name = SEARCH_PRODUCTS, value = "Searches the product catalog, 10 results per page, most reviewed "
            + "first, with price, rating and whether it is in stock. Give at least a query or a category.")
    @ToolLabel("Searching products\u2026")
    public String searchProducts(
            @P(value = "Words to search for in title or brand, e.g. \"noise cancelling headphones\"",
                    required = false) String query,
            @P(value = "Category slug, e.g. headphones, earbuds, audio, electronics, monitors, coffee-makers, "
                    + "home-kitchen, running-shoes, fashion", required = false) String category,
            @P(value = "Maximum price in USD", required = false) Double maxPrice,
            @P(value = "1-based page number; omit for the first page", required = false) Integer page) {
        return run(SEARCH_PRODUCTS, () -> {
            boolean noQuery = query == null || query.isBlank();
            if (noQuery && (category == null || category.isBlank())) {
                throw PortException.invalid("give a query or a category");
            }
            if (query != null && query.length() > 200) throw PortException.invalid("query is too long");
            BigDecimal max = maxPrice == null ? null : BigDecimal.valueOf(maxPrice);
            return catalog.search(ctx, query, category, max, page(page), PAGE_SIZE);
        });
    }

    @Tool(name = GET_PRODUCT, value = "Gets one product with description, price, list price, seller, rating, "
            + "available stock, restock date and attributes.")
    @ToolLabel("Opening the product\u2026")
    public String getProduct(@P("Numeric product id") Long productId) {
        return run(GET_PRODUCT, () -> {
            requireId("productId", productId);
            return catalog.getProduct(ctx, productId);
        });
    }
}
