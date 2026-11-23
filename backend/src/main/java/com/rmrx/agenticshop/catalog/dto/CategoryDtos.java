package com.rmrx.agenticshop.catalog.dto;

import com.rmrx.agenticshop.catalog.entity.AttributeDataType;

import java.math.BigDecimal;
import java.util.List;

/** Category-related response records. */
public final class CategoryDtos {

    private CategoryDtos() {
    }

    public record CategoryRef(Long id, String name, String slug) {
    }

    public record CategoryNode(Long id, String name, String slug, int level, boolean leaf,
                               List<CategoryNode> children) {
    }

    public record CategoryDetail(Long id, String name, String slug, int level, boolean leaf, String description,
                                 List<CategoryRef> breadcrumbs, List<CategoryRef> children) {
    }

    public record AttributeOptionDto(String value, String label) {
    }

    /** A filter the UI can render for a category: options for ENUM types, a range for NUMBER types. */
    public record AttributeFilter(String code, String name, AttributeDataType dataType, String unit,
                                  BigDecimal minValue, BigDecimal maxValue, List<AttributeOptionDto> options) {
    }
}
