package com.rmrx.agenticshop.ai.contract.tool.response.product;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rmrx.agenticshop.ai.contract.domain.AttributeType;
import com.rmrx.agenticshop.ai.contract.tool.ToolResponse;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * A category with its subcategories and the attributes products in it can be filtered by: options for ENUM types
 * (use their {@code value}), a range for NUMBER types, true/false for BOOLEAN.
 */
public record CategoryFiltersResponse(Long categoryId, String name, List<String> path,
                                      List<Subcategory> subcategories, List<Filter> filters) implements ToolResponse {

    public record Subcategory(Long id, String name) implements Serializable {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Filter(String code, String name, AttributeType type, String unit, BigDecimal min, BigDecimal max,
                         List<Option> options, Boolean moreOptions) implements Serializable {
    }

    public record Option(String value, String label) implements Serializable {
    }

    @Override
    public Object toAgentSummary() {
        return this;
    }
}
