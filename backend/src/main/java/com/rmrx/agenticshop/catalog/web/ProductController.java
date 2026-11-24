package com.rmrx.agenticshop.catalog.web;

import org.springframework.security.access.prepost.PreAuthorize;
import com.rmrx.agenticshop.catalog.dto.ProductDtos.*;
import com.rmrx.agenticshop.catalog.service.ProductSearchCriteriaParser;
import com.rmrx.agenticshop.catalog.service.ProductService;
import com.rmrx.agenticshop.common.dto.PageResponse;
import com.rmrx.agenticshop.common.exception.BadRequestException;
import com.rmrx.agenticshop.common.web.Paging;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.data.domain.Sort;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@PreAuthorize("hasAuthority('CATALOG_READ')")
@RequestMapping("/api/products")
public class ProductController {

    private static final String FILTER_DOC = """
            Filters: q, categoryId (includes sub-categories), sellerId, brandIds (comma separated), minPrice, maxPrice,
            minRating, inStock, and attribute filters attr.<code>=v1,v2 / attr.<code>.min / attr.<code>.max
            (codes and values from GET /api/categories/{id}/filters).""";

    private final ProductService productService;
    private final ProductSearchCriteriaParser parser;

    public ProductController(ProductService productService, ProductSearchCriteriaParser parser) {
        this.productService = productService;
        this.parser = parser;
    }

    @Operation(summary = "Search and filter products", description = FILTER_DOC
            + " Sort: relevance (default), price_asc, price_desc, newest, rating, bestselling.")
    @GetMapping
    public PageResponse<ProductSummary> search(
            @Parameter(hidden = true) @RequestParam MultiValueMap<String, String> params,
            @RequestParam(defaultValue = "relevance") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size) {
        return productService.search(parser.parse(params), Paging.of(page, size, sort(sort)));
    }

    @Operation(summary = "Facet counts for the filter sidebar", description = FILTER_DOC)
    @GetMapping("/facets")
    public ProductFacets facets(@Parameter(hidden = true) @RequestParam MultiValueMap<String, String> params) {
        return productService.facets(parser.parse(params));
    }

    @GetMapping("/{id}")
    public ProductDetail get(@PathVariable Long id) {
        return productService.get(id);
    }

    @Operation(summary = "Reviews of a product", description = "sort: newest (default) or helpful; rating: 1-5")
    @GetMapping("/{id}/reviews")
    public PageResponse<ReviewDto> reviews(@PathVariable Long id,
                                           @RequestParam(required = false) Short rating,
                                           @RequestParam(defaultValue = "newest") String sort,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "10") int size) {
        Sort s = switch (sort) {
            case "newest" -> Sort.by(Sort.Order.desc("createdAt"));
            case "helpful" -> Sort.by(Sort.Order.desc("helpfulCount"), Sort.Order.desc("createdAt"));
            default -> throw new BadRequestException("Unknown sort: " + sort);
        };
        return productService.reviews(id, rating, Paging.of(page, size, s));
    }

    @GetMapping("/{id}/reviews/summary")
    public RatingSummary ratingSummary(@PathVariable Long id) {
        return productService.ratingSummary(id);
    }

    private static Sort sort(String sort) {
        Sort s = switch (sort) {
            // No full-text ranking yet: "relevance" favours popular products.
            case "relevance", "bestselling" -> Sort.by(Sort.Order.desc("salesCount"));
            case "price_asc" -> Sort.by(Sort.Order.asc("price"));
            case "price_desc" -> Sort.by(Sort.Order.desc("price"));
            case "newest" -> Sort.by(Sort.Order.desc("createdAt"));
            case "rating" -> Sort.by(Sort.Order.desc("ratingAvg").nullsLast(), Sort.Order.desc("ratingCount"));
            default -> throw new BadRequestException("Unknown sort: " + sort);
        };
        return s.and(Sort.by("id"));
    }
}
