package com.rmrx.agenticshop.common.repository;

import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;
import java.util.Locale;

/**
 * Small null-safe Specification helpers: a filter whose value is null (or empty) matches everything,
 * so optional request parameters can be combined without if/else chains.
 * Attribute paths are dot-separated, e.g. "customer.id".
 */
public final class Specs {

    private Specs() {
    }

    public static <T> Specification<T> all() {
        return (root, query, cb) -> cb.conjunction();
    }

    public static <T> Specification<T> eq(String attribute, Object value) {
        if (value == null) return all();
        return (root, query, cb) -> cb.equal(path(root, attribute), value);
    }

    public static <T> Specification<T> in(String attribute, Collection<?> values) {
        if (values == null || values.isEmpty()) return all();
        return (root, query, cb) -> path(root, attribute).in(values);
    }

    public static <T, Y extends Comparable<? super Y>> Specification<T> gte(String attribute, Y value) {
        if (value == null) return all();
        return (root, query, cb) -> cb.greaterThanOrEqualTo(path(root, attribute), value);
    }

    public static <T, Y extends Comparable<? super Y>> Specification<T> lt(String attribute, Y value) {
        if (value == null) return all();
        return (root, query, cb) -> cb.lessThan(path(root, attribute), value);
    }

    public static <T, Y extends Comparable<? super Y>> Specification<T> lte(String attribute, Y value) {
        if (value == null) return all();
        return (root, query, cb) -> cb.lessThanOrEqualTo(path(root, attribute), value);
    }

    /** Case-insensitive "contains" over one or more string attributes (OR-ed). */
    public static <T> Specification<T> containsIgnoreCase(String text, String... attributes) {
        if (text == null || text.isBlank()) return all();
        String pattern = "%" + text.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> {
            var predicates = new jakarta.persistence.criteria.Predicate[attributes.length];
            for (int i = 0; i < attributes.length; i++) {
                predicates[i] = cb.like(cb.lower(path(root, attributes[i])), pattern);
            }
            return cb.or(predicates);
        };
    }

    @SuppressWarnings("unchecked")
    public static <Y> Path<Y> path(Root<?> root, String attribute) {
        Path<?> path = root;
        for (String part : attribute.split("\\.")) {
            path = path.get(part);
        }
        return (Path<Y>) path;
    }
}
