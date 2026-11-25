package com.rmrx.agenticshop.app.catalog.repository;

import com.rmrx.agenticshop.app.catalog.entity.Inventory;
import com.rmrx.agenticshop.app.catalog.entity.Product;
import com.rmrx.agenticshop.app.catalog.entity.ProductAttributeValue;
import com.rmrx.agenticshop.app.catalog.entity.ProductStatus;
import com.rmrx.agenticshop.app.catalog.service.ProductSearchCriteria;
import com.rmrx.agenticshop.app.catalog.service.ProductSearchCriteria.AttributeCriterion;
import com.rmrx.agenticshop.app.common.repository.Specs;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Translates ProductSearchCriteria into a JPA Specification. */
public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    /**
     * @param categoryPath materialized path of the selected category (subtree match), or null
     */
    public static Specification<Product> of(ProductSearchCriteria c, String categoryPath) {
        List<Specification<Product>> parts = new ArrayList<>();
        parts.add(Specs.eq("status", ProductStatus.ACTIVE));
        parts.add(text(c.q()));
        if (categoryPath != null) parts.add(inCategoryTree(categoryPath));
        parts.add(Specs.eq("seller.id", c.sellerId()));
        parts.add(Specs.in("brand.id", c.brandIds()));
        parts.add(Specs.gte("price", c.minPrice()));
        parts.add(Specs.lte("price", c.maxPrice()));
        parts.add(Specs.gte("ratingAvg", c.minRating()));
        if (Boolean.TRUE.equals(c.inStock())) parts.add(inStock());
        c.attributes().forEach(a -> parts.add(attribute(a)));
        return Specification.allOf(parts);
    }

    /** Every word of the query must appear in the title (backed by the pg_trgm index on lower(title)). */
    static Specification<Product> text(String q) {
        if (q == null || q.isBlank()) return Specs.all();
        String[] words = q.trim().toLowerCase(Locale.ROOT).split("\\s+");
        return (root, query, cb) -> {
            Expression<String> title = cb.lower(root.get("title"));
            Predicate[] predicates = new Predicate[words.length];
            for (int i = 0; i < words.length; i++) {
                predicates[i] = cb.like(title, "%" + words[i] + "%");
            }
            return cb.and(predicates);
        };
    }

    static Specification<Product> inCategoryTree(String path) {
        return (root, query, cb) -> cb.like(root.get("category").get("path"), path + "%");
    }

    static Specification<Product> inStock() {
        return (root, query, cb) -> {
            Subquery<Long> sq = query.subquery(Long.class);
            Root<Inventory> inv = sq.from(Inventory.class);
            sq.select(inv.get("productId")).where(
                    cb.equal(inv.get("productId"), root.get("id")),
                    cb.gt(cb.diff(inv.<Integer>get("qtyAvailable"), inv.<Integer>get("qtyReserved")), 0));
            return cb.exists(sq);
        };
    }

    static Specification<Product> attribute(AttributeCriterion a) {
        return (root, query, cb) -> {
            Subquery<Long> sq = query.subquery(Long.class);
            Root<ProductAttributeValue> v = sq.from(ProductAttributeValue.class);
            List<Predicate> where = new ArrayList<>();
            where.add(cb.equal(v.get("product").get("id"), root.get("id")));
            where.add(cb.equal(v.get("attribute").get("id"), a.attribute().id()));
            switch (a.attribute().dataType()) {
                case ENUM, MULTI_ENUM -> where.add(v.get("option").get("id").in(a.optionIds()));
                case BOOLEAN -> where.add(cb.equal(v.get("boolValue"), a.boolValue()));
                case NUMBER -> {
                    Path<java.math.BigDecimal> num = v.get("numberValue");
                    if (!a.numbers().isEmpty()) where.add(num.in(a.numbers()));
                    if (a.min() != null) where.add(cb.greaterThanOrEqualTo(num, a.min()));
                    if (a.max() != null) where.add(cb.lessThanOrEqualTo(num, a.max()));
                }
            }
            sq.select(v.get("id")).where(where.toArray(Predicate[]::new));
            return cb.exists(sq);
        };
    }
}
