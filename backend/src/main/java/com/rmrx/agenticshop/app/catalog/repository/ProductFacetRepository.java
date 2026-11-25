package com.rmrx.agenticshop.app.catalog.repository;

import com.rmrx.agenticshop.app.catalog.entity.Product;
import jakarta.persistence.Tuple;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;
import java.util.List;

/**
 * Custom Spring Data fragment: aggregate queries over the set of products matched by a Specification,
 * used to build the filter sidebar (facet counts).
 */
public interface ProductFacetRepository {

    /** Tuples: brandId, brandName, count. Ordered by count desc. */
    List<Tuple> countByBrand(Specification<Product> spec);

    /** Tuples: categoryId, count (per leaf category). */
    List<Tuple> countByCategory(Specification<Product> spec);

    /** Tuple: minPrice, maxPrice, count. */
    Tuple priceStats(Specification<Product> spec);

    /** Tuple: count of products with rating_avg >= 4, >= 3, >= 2, >= 1. */
    Tuple ratingStats(Specification<Product> spec);

    /** Tuples: attributeId, optionId, numberValue, boolValue, count for the given attributes. */
    List<Tuple> countAttributeValues(Specification<Product> spec, Collection<Long> attributeIds);
}
