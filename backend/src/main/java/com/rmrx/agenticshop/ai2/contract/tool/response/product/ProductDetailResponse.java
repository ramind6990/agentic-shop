package com.rmrx.agenticshop.ai2.contract.tool.response.product;

import com.rmrx.agenticshop.ai2.contract.tool.FreeText;
import com.rmrx.agenticshop.ai2.contract.tool.ToolResponse;
import com.rmrx.agenticshop.ai2.contract.tool.Texts;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Everything about one product. {@code delisted}: no longer sold (still shown because past orders refer to it).
 *
 * @param categoryPath      category names from the root down
 * @param ratingDistribution stars → number of reviews
 */
public record ProductDetailResponse(ProductItem card, @FreeText String description, String seller,
                                    List<String> categoryPath,
                                    List<Attribute> attributes, boolean delisted, int availableUnits,
                                    LocalDate restockEta, Map<Integer, Long> ratingDistribution)
        implements ToolResponse {

    public record Attribute(String name, String value) implements Serializable {
    }

    public record Summary(ProductItem.Summary product, @FreeText String description, String seller,
                          List<String> categoryPath,
                          List<Attribute> attributes, boolean delisted, LocalDate restockEta,
                          Map<Integer, Long> ratingDistribution) implements Serializable {
    }

    @Override
    public Summary toAgentSummary() {
        return new Summary(card.toSummary(), Texts.cut(description, 300), seller, categoryPath, attributes, delisted,
                restockEta, ratingDistribution);
    }

    @Override
    public List<String> itemIdentifiers() {
        return List.of(String.valueOf(card.id()));
    }
}
