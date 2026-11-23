package com.rmrx.agenticshop.catalog.service;

import com.rmrx.agenticshop.catalog.service.CatalogMetadata.AttributeInfo;
import com.rmrx.agenticshop.catalog.service.ProductSearchCriteria.AttributeCriterion;
import com.rmrx.agenticshop.common.exception.BadRequestException;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;

import java.math.BigDecimal;
import java.util.*;

/**
 * Builds ProductSearchCriteria from raw query parameters. Attribute filters use the form
 * {@code attr.<code>=v1,v2} (options, numbers or true/false) and {@code attr.<code>.min / .max} (numbers).
 */
@Component
public class ProductSearchCriteriaParser {

    private static final String ATTR_PREFIX = "attr.";

    private final CatalogMetadata metadata;

    public ProductSearchCriteriaParser(CatalogMetadata metadata) {
        this.metadata = metadata;
    }

    public ProductSearchCriteria parse(MultiValueMap<String, String> params) {
        return new ProductSearchCriteria(
                blankToNull(params.getFirst("q")),
                longParam(params, "categoryId"),
                longParam(params, "sellerId"),
                longList(params, "brandIds"),
                decimalParam(params, "minPrice"),
                decimalParam(params, "maxPrice"),
                decimalParam(params, "minRating"),
                params.containsKey("inStock") ? Boolean.parseBoolean(params.getFirst("inStock")) : null,
                attributes(params));
    }

    private List<AttributeCriterion> attributes(MultiValueMap<String, String> params) {
        Map<String, Map<String, String>> byCode = new LinkedHashMap<>();
        params.forEach((key, values) -> {
            if (!key.startsWith(ATTR_PREFIX) || values.isEmpty()) return;
            String rest = key.substring(ATTR_PREFIX.length());
            String suffix = "";
            if (rest.endsWith(".min") || rest.endsWith(".max")) {
                suffix = rest.substring(rest.length() - 3);
                rest = rest.substring(0, rest.length() - 4);
            }
            byCode.computeIfAbsent(rest, k -> new HashMap<>()).put(suffix, String.join(",", values));
        });

        List<AttributeCriterion> result = new ArrayList<>();
        byCode.forEach((code, parts) -> {
            AttributeInfo attr = metadata.attributeByCode(code)
                    .orElseThrow(() -> new BadRequestException("Unknown attribute filter: " + code));
            List<String> values = split(parts.get(""));
            switch (attr.dataType()) {
                case ENUM, MULTI_ENUM -> {
                    List<Long> optionIds = values.stream()
                            .map(v -> attr.option(v).orElseThrow(() ->
                                    new BadRequestException("Unknown value '" + v + "' for attribute " + code)).id())
                            .toList();
                    if (!optionIds.isEmpty()) {
                        result.add(new AttributeCriterion(attr, optionIds, List.of(), null, null, null));
                    }
                }
                case BOOLEAN -> {
                    if (!values.isEmpty()) {
                        result.add(new AttributeCriterion(attr, List.of(), List.of(), null, null,
                                Boolean.parseBoolean(values.getFirst())));
                    }
                }
                case NUMBER -> {
                    List<BigDecimal> numbers = values.stream().map(v -> decimal(code, v)).toList();
                    BigDecimal min = parts.containsKey("min") ? decimal(code, parts.get("min")) : null;
                    BigDecimal max = parts.containsKey("max") ? decimal(code, parts.get("max")) : null;
                    if (!numbers.isEmpty() || min != null || max != null) {
                        result.add(new AttributeCriterion(attr, List.of(), numbers, min, max, null));
                    }
                }
            }
        });
        return result;
    }

    private static List<String> split(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static Long longParam(MultiValueMap<String, String> params, String name) {
        String v = blankToNull(params.getFirst(name));
        if (v == null) return null;
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            throw new BadRequestException("Invalid value '" + v + "' for parameter '" + name + "'");
        }
    }

    private static List<Long> longList(MultiValueMap<String, String> params, String name) {
        List<String> raw = params.getOrDefault(name, List.of());
        try {
            return raw.stream().flatMap(r -> split(r).stream()).map(Long::parseLong).toList();
        } catch (NumberFormatException e) {
            throw new BadRequestException("Invalid value for parameter '" + name + "'");
        }
    }

    private static BigDecimal decimalParam(MultiValueMap<String, String> params, String name) {
        String v = blankToNull(params.getFirst(name));
        return v == null ? null : decimal(name, v);
    }

    private static BigDecimal decimal(String name, String v) {
        try {
            return new BigDecimal(v.trim());
        } catch (NumberFormatException e) {
            throw new BadRequestException("Invalid number '" + v + "' for '" + name + "'");
        }
    }
}
