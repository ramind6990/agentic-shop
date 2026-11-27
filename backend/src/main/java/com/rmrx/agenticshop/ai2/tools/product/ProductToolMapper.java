package com.rmrx.agenticshop.ai2.tools.product;

import com.rmrx.agenticshop.ai2.contract.domain.AttributeType;
import com.rmrx.agenticshop.ai2.contract.tool.response.product.CategoryFiltersResponse;
import com.rmrx.agenticshop.ai2.contract.tool.response.product.ProductDetailResponse;
import com.rmrx.agenticshop.ai2.contract.tool.response.product.ProductItem;
import com.rmrx.agenticshop.ai2.contract.tool.response.product.ProductListResponse.Facet;
import com.rmrx.agenticshop.ai2.contract.tool.response.product.ProductListResponse.Refinements;
import com.rmrx.agenticshop.ai2.contract.tool.response.product.ReviewListResponse;
import com.rmrx.agenticshop.app.catalog.dto.CategoryDtos.AttributeFilter;
import com.rmrx.agenticshop.app.catalog.dto.CategoryDtos.CategoryDetail;
import com.rmrx.agenticshop.app.catalog.dto.CategoryDtos.CategoryRef;
import com.rmrx.agenticshop.app.catalog.dto.ProductDtos.*;
import com.rmrx.agenticshop.app.catalog.entity.AttributeDataType;
import com.rmrx.agenticshop.app.catalog.entity.ProductStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Comparator;
import java.util.List;

/** Catalog DTOs → ai2 product contract. */
@Mapper
public interface ProductToolMapper {

    int TOP_REFINEMENTS = 5;
    int MAX_OPTIONS = 25;

    @Mapping(target = "brand", source = "brand.name")
    @Mapping(target = "category", source = "category.name")
    @Mapping(target = "rating", source = "ratingAvg")
    ProductItem toItem(ProductSummary product);

    @Mapping(target = "brand", source = "brand.name")
    @Mapping(target = "category", expression = "java(lastName(d.breadcrumbs()))")
    @Mapping(target = "rating", source = "ratingAvg")
    @Mapping(target = "inStock", source = "stock.inStock")
    @Mapping(target = "thumbnailUrl", expression = "java(d.images().isEmpty() ? null : d.images().getFirst().url())")
    ProductItem toCard(ProductDetail d);

    ReviewListResponse.Review toReview(ReviewDto review);

    AttributeType toType(AttributeDataType type);

    default ProductDetailResponse toDetail(ProductDetail d, RatingSummary ratings) {
        return new ProductDetailResponse(toCard(d), d.description(), d.seller().name(),
                d.breadcrumbs().stream().map(CategoryRef::name).toList(),
                d.attributes().stream().map(a -> new ProductDetailResponse.Attribute(a.name(),
                        String.join(", ", a.labels()))).toList(),
                d.status() == ProductStatus.DELISTED, d.stock().available(), d.stock().restockEta(),
                ratings.distribution());
    }

    default Refinements toRefinements(ProductFacets facets) {
        return new Refinements(
                facets.categories().stream().sorted(Comparator.comparingLong(CategoryFacet::count).reversed())
                        .limit(TOP_REFINEMENTS).map(c -> new Facet(c.id(), c.name(), c.count())).toList(),
                facets.brands().stream().sorted(Comparator.comparingLong(FacetValue::count).reversed())
                        .limit(TOP_REFINEMENTS).map(b -> new Facet(Long.valueOf(b.value()), b.label(), b.count()))
                        .toList(),
                facets.price().min(), facets.price().max());
    }

    default CategoryFiltersResponse toFilters(CategoryDetail c, List<AttributeFilter> filters) {
        return new CategoryFiltersResponse(c.id(), c.name(), c.breadcrumbs().stream().map(CategoryRef::name).toList(),
                c.children().stream().map(s -> new CategoryFiltersResponse.Subcategory(s.id(), s.name())).toList(),
                filters.stream().map(this::toFilter).toList());
    }

    default CategoryFiltersResponse.Filter toFilter(AttributeFilter f) {
        List<CategoryFiltersResponse.Option> options = f.options().stream().limit(MAX_OPTIONS)
                .map(o -> new CategoryFiltersResponse.Option(o.value(), o.label())).toList();
        return new CategoryFiltersResponse.Filter(f.code(), f.name(),
                toType(f.dataType()), f.unit(),
                f.minValue(), f.maxValue(), options.isEmpty() ? null : options,
                f.options().size() > MAX_OPTIONS ? Boolean.TRUE : null);
    }

    default String lastName(List<CategoryRef> breadcrumbs) {
        return breadcrumbs.isEmpty() ? null : breadcrumbs.getLast().name();
    }
}
