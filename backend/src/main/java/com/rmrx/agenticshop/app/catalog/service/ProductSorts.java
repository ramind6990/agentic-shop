package com.rmrx.agenticshop.app.catalog.service;

import com.rmrx.agenticshop.app.common.exception.BadRequestException;
import org.springframework.data.domain.Sort;

/** The sort orders of product listings and review lists, by their public keys. */
public final class ProductSorts {

    private ProductSorts() {
    }

    /** relevance, bestselling, price_asc, price_desc, newest, rating; ties broken by id. */
    public static Sort products(String key) {
        Sort s = switch (key) {
            // No full-text ranking yet: "relevance" favours popular products.
            case "relevance", "bestselling" -> Sort.by(Sort.Order.desc("salesCount"));
            case "price_asc" -> Sort.by(Sort.Order.asc("price"));
            case "price_desc" -> Sort.by(Sort.Order.desc("price"));
            case "newest" -> Sort.by(Sort.Order.desc("createdAt"));
            case "rating" -> Sort.by(Sort.Order.desc("ratingAvg").nullsLast(), Sort.Order.desc("ratingCount"));
            default -> throw new BadRequestException("Unknown sort: " + key);
        };
        return s.and(Sort.by("id"));
    }

    /** newest, helpful. */
    public static Sort reviews(String key) {
        return switch (key) {
            case "newest" -> Sort.by(Sort.Order.desc("createdAt"));
            case "helpful" -> Sort.by(Sort.Order.desc("helpfulCount"), Sort.Order.desc("createdAt"));
            default -> throw new BadRequestException("Unknown sort: " + key);
        };
    }
}
