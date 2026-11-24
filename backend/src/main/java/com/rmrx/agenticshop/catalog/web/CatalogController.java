package com.rmrx.agenticshop.catalog.web;

import org.springframework.security.access.prepost.PreAuthorize;
import com.rmrx.agenticshop.catalog.dto.CategoryDtos.AttributeFilter;
import com.rmrx.agenticshop.catalog.dto.CategoryDtos.CategoryDetail;
import com.rmrx.agenticshop.catalog.dto.CategoryDtos.CategoryNode;
import com.rmrx.agenticshop.catalog.service.BrandService;
import com.rmrx.agenticshop.catalog.service.CategoryService;
import com.rmrx.agenticshop.common.dto.PageResponse;
import com.rmrx.agenticshop.common.dto.Ref;
import com.rmrx.agenticshop.common.web.Paging;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@PreAuthorize("hasAuthority('CATALOG_READ')")
@RequestMapping("/api")
public class CatalogController {

    private final CategoryService categoryService;
    private final BrandService brandService;

    public CatalogController(CategoryService categoryService, BrandService brandService) {
        this.categoryService = categoryService;
        this.brandService = brandService;
    }

    /** Whole category tree (roots with nested children). */
    @GetMapping("/categories")
    public List<CategoryNode> categoryTree() {
        return categoryService.tree();
    }

    @GetMapping("/categories/{idOrSlug}")
    public CategoryDetail category(@PathVariable String idOrSlug) {
        return categoryService.get(idOrSlug);
    }

    /** Filterable attributes (inherited from ancestors too) with their allowed options. */
    @GetMapping("/categories/{idOrSlug}/filters")
    public List<AttributeFilter> categoryFilters(@PathVariable String idOrSlug) {
        return categoryService.filters(idOrSlug);
    }

    @GetMapping("/brands")
    public PageResponse<Ref> brands(@RequestParam(required = false) String q,
                                    @RequestParam(required = false) Long categoryId,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "50") int size) {
        return brandService.search(q, categoryId, Paging.of(page, size, Sort.by("name")));
    }
}
