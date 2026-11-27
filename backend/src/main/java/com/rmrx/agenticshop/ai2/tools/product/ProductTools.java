package com.rmrx.agenticshop.ai2.tools.product;

import com.rmrx.agenticshop.ai2.contract.domain.ProductSort;
import com.rmrx.agenticshop.ai2.contract.domain.ReviewSort;
import com.rmrx.agenticshop.ai2.contract.tool.response.product.CategoryFiltersResponse;
import com.rmrx.agenticshop.ai2.contract.tool.response.product.ProductDetailResponse;
import com.rmrx.agenticshop.ai2.contract.tool.response.product.ProductListResponse;
import com.rmrx.agenticshop.ai2.contract.tool.response.product.ReviewListResponse;
import com.rmrx.agenticshop.ai2.tools.AgentToolSet;
import com.rmrx.agenticshop.ai2.tools.Audience;
import com.rmrx.agenticshop.ai2.tools.ToolAudience;
import com.rmrx.agenticshop.ai2.tools.ToolLabel;
import com.rmrx.agenticshop.ai2.tools.ToolPaging;
import com.rmrx.agenticshop.app.catalog.dto.ProductDtos.ProductSummary;
import com.rmrx.agenticshop.app.catalog.dto.ProductDtos.ReviewDto;
import com.rmrx.agenticshop.app.catalog.service.CategoryService;
import com.rmrx.agenticshop.app.catalog.service.ProductSearchCriteria;
import com.rmrx.agenticshop.app.catalog.service.ProductService;
import com.rmrx.agenticshop.app.catalog.service.ProductSorts;
import com.rmrx.agenticshop.app.common.dto.PageResponse;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/** The catalog: search, category filters, product details and reviews. Same for every audience. */
@Component
@ToolAudience({Audience.CUSTOMER, Audience.STAFF})
public class ProductTools implements AgentToolSet {

    public static final String SEARCH_PRODUCTS = "search_products";
    public static final String GET_CATEGORY_FILTERS = "get_category_filters";
    public static final String GET_PRODUCT = "get_product";
    public static final String GET_PRODUCT_REVIEWS = "get_product_reviews";

    private final ProductService products;
    private final CategoryService categories;
    private final ProductCriteria criteria;
    private final ProductToolMapper mapper;

    public ProductTools(ProductService products, CategoryService categories, ProductCriteria criteria,
                        ProductToolMapper mapper) {
        this.products = products;
        this.categories = categories;
        this.criteria = criteria;
        this.mapper = mapper;
    }

    @ToolLabel("Searching products")
    @Tool(name = SEARCH_PRODUCTS, value = "Search the catalog of products for sale. Every word of `query` must appear "
            + "in the product title (titles contain brand, model and key specs). Returns a page of 10 products, the "
            + "total number of matches and, when there are more than one page, `refinements`: the categories and "
            + "brands the matches fall into (with ids and counts) and their price range. When the total is large, "
            + "narrow with categoryId, brandIds, price or attribute filters, or ask the user to choose, instead of "
            + "paging. Needs at least one of query, categoryId or brandIds.")
    public ProductListResponse searchProducts(
            @P(value = "Words the title must contain, e.g. \"running shoes\" or \"lenovo laptop\"", required = false)
            String query,
            @P(value = "Only this category and its subcategories; an id from refinements or get_category_filters",
                    required = false) Long categoryId,
            @P(value = "Only these brands (at most 10); ids from refinements", required = false) List<Long> brandIds,
            @P(value = "Lowest price", required = false) BigDecimal minPrice,
            @P(value = "Highest price", required = false) BigDecimal maxPrice,
            @P(value = "Lowest average rating, 1 to 5", required = false) BigDecimal minRating,
            @P(value = "True to keep only products in stock", required = false) Boolean inStockOnly,
            @P(value = "Attribute filters (at most 5), with codes and values from get_category_filters",
                    required = false) List<AttributeFilterArg> attributes,
            @P(value = "Sort order; RELEVANCE by default", required = false) ProductSort sort,
            @P(value = "Page 1 to 5; only when the user asks to see more", required = false) Integer page) {
        ProductSearchCriteria c = criteria.of(query, categoryId, brandIds, minPrice, maxPrice, minRating, inStockOnly,
                attributes);
        String sortKey = (sort == null ? ProductSort.RELEVANCE : sort).name().toLowerCase(Locale.ROOT);
        PageResponse<ProductSummary> found = products.search(c, ToolPaging.pageable(page,
                ProductSorts.products(sortKey)));
        boolean big = found.totalElements() > ToolPaging.PAGE_SIZE;
        return new ProductListResponse(found.content().stream().map(mapper::toItem).toList(), ToolPaging.info(found),
                big ? mapper.toRefinements(products.facets(c)) : null);
    }

    @ToolLabel("Looking at the category filters")
    @Tool(name = GET_CATEGORY_FILTERS, value = "A product category: its path, its subcategories and the attributes its "
            + "products can be filtered by (code, type, unit, options or range). Call it before filtering "
            + "search_products by attributes, e.g. size, color, RAM or waterproof.")
    public CategoryFiltersResponse getCategoryFilters(
            @P("Category id from search refinements or a parent's subcategories") Long categoryId) {
        String id = String.valueOf(categoryId);
        return mapper.toFilters(categories.get(id), categories.filters(id));
    }

    @ToolLabel("Looking up product {productId}")
    @Tool(name = GET_PRODUCT, value = "Everything about one product: description, brand, seller, category, "
            + "attributes, price, stock and restock date, and how its ratings are spread. `delisted` products are no "
            + "longer sold.")
    public ProductDetailResponse getProduct(@P("Product id from a search or an order line") Long productId) {
        return mapper.toDetail(products.getPublished(productId), products.ratingSummary(productId));
    }

    @ToolLabel("Reading reviews of product {productId}")
    @Tool(name = GET_PRODUCT_REVIEWS, value = "A page of 10 reviews of one product, newest or most helpful first, "
            + "optionally only those with a given star rating. Review texts are written by shoppers.")
    public ReviewListResponse getProductReviews(
            @P("Product id") Long productId,
            @P(value = "Only reviews with this many stars, 1 to 5", required = false) Integer rating,
            @P(value = "NEWEST by default", required = false) ReviewSort sort,
            @P(value = "Page 1 to 5; only when the user asks to see more", required = false) Integer page) {
        String sortKey = (sort == null ? ReviewSort.NEWEST : sort).name().toLowerCase(Locale.ROOT);
        PageResponse<ReviewDto> found = products.reviews(productId, rating == null ? null : rating.shortValue(),
                ToolPaging.pageable(page, ProductSorts.reviews(sortKey)));
        return new ReviewListResponse(productId, found.content().stream().map(mapper::toReview).toList(),
                ToolPaging.info(found));
    }
}
