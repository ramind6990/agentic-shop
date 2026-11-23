package com.rmrx.agenticshop.catalog.service;

import com.rmrx.agenticshop.catalog.service.CatalogMetadata.AttributeInfo;

import java.math.BigDecimal;
import java.util.List;

/**
 * Parsed product search filters. Copy methods (without*) drop one filter; they are used for
 * disjunctive faceting, where the counts for a filter group ignore that group's own selection.
 */
public record ProductSearchCriteria(
        String q,
        Long categoryId,
        Long sellerId,
        List<Long> brandIds,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        BigDecimal minRating,
        Boolean inStock,
        List<AttributeCriterion> attributes) {

    /**
     * Filter on one attribute. ENUM/MULTI_ENUM: any of optionIds. NUMBER: any of numbers and/or the min..max range.
     * BOOLEAN: boolValue.
     */
    public record AttributeCriterion(AttributeInfo attribute, List<Long> optionIds, List<BigDecimal> numbers,
                                     BigDecimal min, BigDecimal max, Boolean boolValue) {
    }

    public ProductSearchCriteria withoutBrands() {
        return new ProductSearchCriteria(q, categoryId, sellerId, List.of(), minPrice, maxPrice, minRating, inStock,
                attributes);
    }

    public ProductSearchCriteria withoutPrice() {
        return new ProductSearchCriteria(q, categoryId, sellerId, brandIds, null, null, minRating, inStock,
                attributes);
    }

    public ProductSearchCriteria withoutRating() {
        return new ProductSearchCriteria(q, categoryId, sellerId, brandIds, minPrice, maxPrice, null, inStock,
                attributes);
    }

    public ProductSearchCriteria withoutAttribute(Long attributeId) {
        return new ProductSearchCriteria(q, categoryId, sellerId, brandIds, minPrice, maxPrice, minRating, inStock,
                attributes.stream().filter(a -> !a.attribute().id().equals(attributeId)).toList());
    }

    public boolean hasAttributeFilter(Long attributeId) {
        return attributes.stream().anyMatch(a -> a.attribute().id().equals(attributeId));
    }
}
