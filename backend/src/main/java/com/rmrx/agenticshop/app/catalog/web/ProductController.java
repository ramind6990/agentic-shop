package com.rmrx.agenticshop.app.catalog.web;

import org.springframework.security.access.prepost.PreAuthorize;
import com.rmrx.agenticshop.app.catalog.dto.ProductDtos.*;
import com.rmrx.agenticshop.app.catalog.service.ProductSearchCriteriaParser;
import com.rmrx.agenticshop.app.catalog.service.ProductService;
import com.rmrx.agenticshop.app.catalog.service.ProductSorts;
import com.rmrx.agenticshop.app.common.dto.PageResponse;
import com.rmrx.agenticshop.app.common.web.Paging;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
        return productService.search(parser.parse(params), Paging.of(page, size, ProductSorts.products(sort)));
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
        return productService.reviews(id, rating, Paging.of(page, size, ProductSorts.reviews(sort)));
    }

    @GetMapping("/{id}/reviews/summary")
    public RatingSummary ratingSummary(@PathVariable Long id) {
        return productService.ratingSummary(id);
    }
}
