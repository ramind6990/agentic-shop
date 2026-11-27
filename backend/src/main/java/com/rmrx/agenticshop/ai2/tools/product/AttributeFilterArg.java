package com.rmrx.agenticshop.ai2.tools.product;

import dev.langchain4j.model.output.structured.Description;

import java.math.BigDecimal;
import java.util.List;

/** One attribute filter of a product search, as the model sends it. */
public record AttributeFilterArg(
        @Description("Attribute code from get_category_filters, e.g. \"ram_gb\"") String code,
        @Description("Accepted values: option values for ENUM types, numbers for NUMBER, \"true\"/\"false\" for "
                + "BOOLEAN; any of them matches") List<String> values,
        @Description("Smallest accepted number (NUMBER types only)") BigDecimal min,
        @Description("Largest accepted number (NUMBER types only)") BigDecimal max) {
}
