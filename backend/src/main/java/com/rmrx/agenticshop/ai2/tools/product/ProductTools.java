package com.rmrx.agenticshop.ai2.tools.product;

import com.rmrx.agenticshop.ai2.contract.tool.response.ProductItem;
import com.rmrx.agenticshop.ai2.contract.tool.response.ProductListResponse;
import com.rmrx.agenticshop.ai2.tools.AgentToolSet;
import com.rmrx.agenticshop.ai2.tools.ToolLabel;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Product tools of the agent.
 */
@Component
public class ProductTools implements AgentToolSet {

    public static final String SEARCH_PRODUCTS = "search_products";

    @ToolLabel("Searching products for {query}")
    @Tool(name = SEARCH_PRODUCTS, value = "Search the catalog for products matching a text query. Returns a page of "
            + "products with total matches and whether more exist.")
    public ProductListResponse searchProducts(@P("What the user is looking for, e.g. \"red running shoes\"") String query) {
        // TODO search through the catalog service; canned happy response for now
        List<ProductItem> items = List.of(
                new ProductItem(101L, "SH-RUN-RED-42", "Trail Runner Red",
                        "Grippy trail shoe with a cushioned midsole for long off-road runs.", "Stridex",
                        new BigDecimal("89.90"), new BigDecimal("109.90"), "EUR", new BigDecimal("4.6"), 128, true,
                        "https://cdn.example.com/products/101/thumb.jpg"),
                new ProductItem(102L, "SH-RUN-BLU-42", "City Runner Blue",
                        "Light road shoe for daily runs and commuting.", "Stridex",
                        new BigDecimal("74.50"), new BigDecimal("74.50"), "EUR", new BigDecimal("4.3"), 87, true,
                        "https://cdn.example.com/products/102/thumb.jpg"),
                new ProductItem(103L, "SH-RUN-BLK-42", "Night Runner Black",
                        "Reflective running shoe for low-light runs.", "Lumo",
                        new BigDecimal("99.00"), new BigDecimal("119.00"), "EUR", new BigDecimal("4.8"), 241, false,
                        "https://cdn.example.com/products/103/thumb.jpg"));
        return new ProductListResponse(items, items.size(), false);
    }
}
