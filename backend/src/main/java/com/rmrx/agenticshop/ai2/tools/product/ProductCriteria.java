package com.rmrx.agenticshop.ai2.tools.product;

import com.rmrx.agenticshop.app.catalog.service.ProductSearchCriteria;
import com.rmrx.agenticshop.app.catalog.service.ProductSearchCriteriaParser;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Turns the arguments of {@code search_products} into the catalog's {@link ProductSearchCriteria}, through the same
 * parser as the shop's product listing (so unknown attribute codes or values get the same clear errors).
 */
@Component
class ProductCriteria {

    static final int MAX_BRANDS = 10;
    static final int MAX_ATTRIBUTES = 5;

    private final ProductSearchCriteriaParser parser;

    ProductCriteria(ProductSearchCriteriaParser parser) {
        this.parser = parser;
    }

    ProductSearchCriteria of(String query, Long categoryId, List<Long> brandIds, BigDecimal minPrice,
                             BigDecimal maxPrice, BigDecimal minRating, Boolean inStockOnly,
                             List<AttributeFilterArg> attributes) {
        if (isBlank(query) && categoryId == null && isEmpty(brandIds)) {
            throw new IllegalArgumentException("Give a query, a categoryId or brandIds.");
        }
        if (!isEmpty(brandIds) && brandIds.size() > MAX_BRANDS) {
            throw new IllegalArgumentException("At most " + MAX_BRANDS + " brandIds.");
        }
        if (!isEmpty(attributes) && attributes.size() > MAX_ATTRIBUTES) {
            throw new IllegalArgumentException("At most " + MAX_ATTRIBUTES + " attribute filters.");
        }
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        put(params, "q", query);
        put(params, "categoryId", categoryId);
        if (!isEmpty(brandIds)) put(params, "brandIds", brandIds.stream().map(String::valueOf)
                .collect(Collectors.joining(",")));
        put(params, "minPrice", minPrice);
        put(params, "maxPrice", maxPrice);
        put(params, "minRating", minRating);
        if (Boolean.TRUE.equals(inStockOnly)) params.add("inStock", "true");
        if (attributes != null) attributes.forEach(a -> putAttribute(params, a));
        return parser.parse(params);
    }

    private static void putAttribute(MultiValueMap<String, String> params, AttributeFilterArg a) {
        if (isBlank(a.code())) throw new IllegalArgumentException("Every attribute filter needs a code.");
        String key = "attr." + a.code().trim();
        if (!isEmpty(a.values())) params.add(key, String.join(",", a.values()));
        put(params, key + ".min", a.min());
        put(params, key + ".max", a.max());
    }

    private static void put(MultiValueMap<String, String> params, String key, Object value) {
        if (value != null && !value.toString().isBlank()) params.add(key, value.toString().trim());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static boolean isEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }
}
