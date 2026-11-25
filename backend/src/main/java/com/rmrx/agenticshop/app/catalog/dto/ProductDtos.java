package com.rmrx.agenticshop.app.catalog.dto;

import com.rmrx.agenticshop.app.catalog.dto.CategoryDtos.CategoryRef;
import com.rmrx.agenticshop.app.catalog.entity.AttributeDataType;
import com.rmrx.agenticshop.app.catalog.entity.ProductStatus;
import com.rmrx.agenticshop.app.common.dto.Ref;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Product-related response records. */
public final class ProductDtos {

    private ProductDtos() {
    }

    /** Card in a product listing. */
    public record ProductSummary(Long id, String sku, String title, String slug, BigDecimal price,
                                 BigDecimal listPrice, String currency, BigDecimal ratingAvg, int ratingCount,
                                 int salesCount, Ref brand, Ref seller, Ref category, String thumbnailUrl,
                                 boolean inStock) {
    }

    public record ProductImageDto(String url, String altText) {
    }

    /** One attribute value of a product, ready for display. For MULTI_ENUM, several labels. */
    public record ProductAttributeDto(String code, String name, AttributeDataType dataType, String unit,
                                      List<String> values, List<String> labels) {
    }

    public record StockDto(int available, boolean inStock, String warehouseCode, LocalDate restockEta) {
    }

    public record ProductDetail(Long id, String sku, String title, String slug, String description,
                                BigDecimal price, BigDecimal listPrice, String currency, ProductStatus status,
                                BigDecimal ratingAvg, int ratingCount, int salesCount, Instant createdAt,
                                Ref brand, Ref seller, List<CategoryRef> breadcrumbs,
                                List<ProductImageDto> images, List<ProductAttributeDto> attributes,
                                StockDto stock) {
    }

    public record ReviewDto(Long id, int rating, String title, String body, int helpfulCount,
                            String authorName, boolean verifiedPurchase, Instant createdAt) {
    }

    /** Review written by a customer, with the product it is about. */
    public record CustomerReviewDto(Long id, int rating, String title, String body, Ref product, Instant createdAt) {
    }

    public record RatingSummary(BigDecimal average, long total, Map<Integer, Long> distribution) {
    }

    // ---------- facets ----------

    public record FacetValue(String value, String label, long count) {
    }

    public record CategoryFacet(Long id, String name, String slug, long count) {
    }

    public record RangeFacet(BigDecimal min, BigDecimal max) {
    }

    public record AttributeFacet(String code, String name, AttributeDataType dataType, String unit,
                                 List<FacetValue> values, RangeFacet range) {
    }

    /** Everything a filter sidebar needs for the current search. */
    public record ProductFacets(long total, List<CategoryFacet> categories, List<FacetValue> brands,
                                RangeFacet price, List<FacetValue> ratings, List<AttributeFacet> attributes) {
    }
}
