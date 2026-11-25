package com.rmrx.agenticshop.app.catalog.service;

import com.rmrx.agenticshop.app.catalog.dto.ProductDtos.*;
import com.rmrx.agenticshop.app.catalog.entity.*;
import com.rmrx.agenticshop.app.catalog.repository.*;
import com.rmrx.agenticshop.app.catalog.service.CatalogMetadata.AttributeInfo;
import com.rmrx.agenticshop.app.catalog.service.CatalogMetadata.CategoryAttributeInfo;
import com.rmrx.agenticshop.app.catalog.service.CatalogMetadata.CategoryInfo;
import com.rmrx.agenticshop.app.common.dto.PageResponse;
import com.rmrx.agenticshop.app.common.dto.Ref;
import com.rmrx.agenticshop.app.common.exception.NotFoundException;
import jakarta.persistence.Tuple;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductAttributeValueRepository attributeValueRepository;
    private final ReviewRepository reviewRepository;
    private final CatalogMetadata metadata;
    private final CatalogMapper mapper;

    public ProductService(ProductRepository productRepository, InventoryRepository inventoryRepository,
                          ProductAttributeValueRepository attributeValueRepository,
                          ReviewRepository reviewRepository, CatalogMetadata metadata, CatalogMapper mapper) {
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.attributeValueRepository = attributeValueRepository;
        this.reviewRepository = reviewRepository;
        this.metadata = metadata;
        this.mapper = mapper;
    }

    // ---------- search ----------

    public PageResponse<ProductSummary> search(ProductSearchCriteria criteria, Pageable pageable) {
        Page<Product> page = productRepository.findAll(spec(criteria), pageable);
        List<Long> ids = page.getContent().stream().map(Product::getId).toList();
        Map<Long, Inventory> stock = inventoryRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Inventory::getProductId, Function.identity()));
        return PageResponse.of(page, p -> {
            Inventory inv = stock.get(p.getId());
            return mapper.toSummary(p, inv != null && inv.isInStock());
        });
    }

    // ---------- facets ----------

    /**
     * Facet counts for the filter sidebar. Disjunctive: the counts of a filter group (brands, price, rating,
     * one attribute) are computed without that group's own selection, so the user still sees alternatives.
     */
    public ProductFacets facets(ProductSearchCriteria criteria) {
        Specification<Product> full = spec(criteria);

        Tuple total = productRepository.priceStats(full);
        Tuple priceStats = productRepository.priceStats(spec(criteria.withoutPrice()));
        Tuple ratingStats = productRepository.ratingStats(spec(criteria.withoutRating()));

        List<FacetValue> brands = productRepository.countByBrand(spec(criteria.withoutBrands())).stream()
                .map(t -> new FacetValue(String.valueOf(t.get(0)), (String) t.get(1), (Long) t.get(2)))
                .toList();

        List<FacetValue> ratings = List.of(
                new FacetValue("4", "4 stars & up", nz(ratingStats.get(0))),
                new FacetValue("3", "3 stars & up", nz(ratingStats.get(1))),
                new FacetValue("2", "2 stars & up", nz(ratingStats.get(2))),
                new FacetValue("1", "1 star & up", nz(ratingStats.get(3))));

        return new ProductFacets(
                (Long) total.get(2),
                categoryFacets(criteria),
                brands,
                new RangeFacet((BigDecimal) priceStats.get(0), (BigDecimal) priceStats.get(1)),
                ratings,
                attributeFacets(criteria));
    }

    /** Counts per child of the selected category (or per root category when none is selected). */
    private List<CategoryFacet> categoryFacets(ProductSearchCriteria criteria) {
        List<CategoryInfo> buckets = criteria.categoryId() == null
                ? metadata.roots() : metadata.children(criteria.categoryId());
        if (buckets.isEmpty()) return List.of();
        Map<Long, Long> perLeaf = new HashMap<>();
        productRepository.countByCategory(spec(criteria)).forEach(t -> perLeaf.put((Long) t.get(0), (Long) t.get(1)));
        List<CategoryFacet> result = new ArrayList<>();
        for (CategoryInfo bucket : buckets) {
            long count = perLeaf.entrySet().stream()
                    .filter(e -> metadata.category(e.getKey()).path().startsWith(bucket.path()))
                    .mapToLong(Map.Entry::getValue).sum();
            if (count > 0) result.add(new CategoryFacet(bucket.id(), bucket.name(), bucket.slug(), count));
        }
        return result;
    }

    private List<AttributeFacet> attributeFacets(ProductSearchCriteria criteria) {
        if (criteria.categoryId() == null) return List.of();
        List<CategoryAttributeInfo> filterable = metadata.effectiveAttributes(criteria.categoryId()).stream()
                .filter(CategoryAttributeInfo::filterable).toList();

        // Attributes without an active selection share one query; each selected attribute gets its own
        // query that ignores its own selection.
        Map<Long, List<Tuple>> rowsByAttribute = new HashMap<>();
        List<Long> unselected = filterable.stream().map(ca -> ca.attribute().id())
                .filter(id -> !criteria.hasAttributeFilter(id)).toList();
        productRepository.countAttributeValues(spec(criteria), unselected)
                .forEach(t -> rowsByAttribute.computeIfAbsent((Long) t.get(0), k -> new ArrayList<>()).add(t));
        for (CategoryAttributeInfo ca : filterable) {
            Long id = ca.attribute().id();
            if (criteria.hasAttributeFilter(id)) {
                rowsByAttribute.put(id, productRepository.countAttributeValues(
                        spec(criteria.withoutAttribute(id)), List.of(id)));
            }
        }

        List<AttributeFacet> result = new ArrayList<>();
        for (CategoryAttributeInfo ca : filterable) {
            AttributeInfo a = ca.attribute();
            List<Tuple> rows = rowsByAttribute.getOrDefault(a.id(), List.of());
            if (rows.isEmpty()) continue;
            result.add(toAttributeFacet(a, rows));
        }
        return result;
    }

    private AttributeFacet toAttributeFacet(AttributeInfo a, List<Tuple> rows) {
        List<FacetValue> values = new ArrayList<>();
        RangeFacet range = null;
        switch (a.dataType()) {
            case ENUM, MULTI_ENUM -> {
                Map<Long, Long> counts = new HashMap<>();
                rows.forEach(t -> counts.merge((Long) t.get(1), (Long) t.get(4), Long::sum));
                a.options().forEach(o -> {
                    Long c = counts.get(o.id());
                    if (c != null) values.add(new FacetValue(o.value(), o.label(), c));
                });
            }
            case BOOLEAN -> rows.forEach(t -> {
                Boolean b = (Boolean) t.get(3);
                if (b != null) values.add(new FacetValue(b.toString(), b ? "Yes" : "No", (Long) t.get(4)));
            });
            case NUMBER -> {
                TreeMap<BigDecimal, Long> counts = new TreeMap<>();
                rows.forEach(t -> {
                    BigDecimal n = (BigDecimal) t.get(2);
                    if (n != null) counts.merge(n.stripTrailingZeros(), (Long) t.get(4), Long::sum);
                });
                counts.forEach((n, c) -> values.add(new FacetValue(n.toPlainString(),
                        n.toPlainString() + (a.unit() == null ? "" : " " + a.unit()), c)));
                if (!counts.isEmpty()) range = new RangeFacet(counts.firstKey(), counts.lastKey());
            }
        }
        return new AttributeFacet(a.code(), a.name(), a.dataType(), a.unit(), values, range);
    }

    // ---------- detail ----------

    public ProductDetail get(Long id) {
        Product p = productRepository.findDetailedById(id).orElseThrow(() -> new NotFoundException("Product", id));
        Inventory inventory = inventoryRepository.findById(id).orElse(null);
        return new ProductDetail(p.getId(), p.getSku(), p.getTitle(), p.getSlug(), p.getDescription(),
                p.getPrice(), p.getListPrice(), p.getCurrency(), p.getStatus(), p.getRatingAvg(),
                p.getRatingCount(), p.getSalesCount(), p.getCreatedAt(),
                new Ref(p.getBrand().getId(), p.getBrand().getName()),
                new Ref(p.getSeller().getId(), p.getSeller().getName()),
                metadata.ancestry(p.getCategory().getId()).stream().map(CategoryService::ref).toList(),
                p.getImages().stream().map(mapper::toImage).toList(),
                attributes(p),
                mapper.toStock(inventory));
    }

    /** Product attribute values, ordered like the category's attribute list. */
    private List<ProductAttributeDto> attributes(Product p) {
        Map<Long, List<ProductAttributeValue>> byAttribute = attributeValueRepository.findByProductId(p.getId())
                .stream().collect(Collectors.groupingBy(v -> v.getAttribute().getId()));
        List<ProductAttributeDto> result = new ArrayList<>();
        for (CategoryAttributeInfo ca : metadata.effectiveAttributes(p.getCategory().getId())) {
            AttributeInfo a = ca.attribute();
            List<ProductAttributeValue> vals = byAttribute.get(a.id());
            if (vals == null) continue;
            List<String> values = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            for (ProductAttributeValue v : vals) {
                switch (a.dataType()) {
                    case ENUM, MULTI_ENUM -> {
                        values.add(v.getOption().getValue());
                        labels.add(v.getOption().getLabel());
                    }
                    case NUMBER -> {
                        String n = v.getNumberValue().stripTrailingZeros().toPlainString();
                        values.add(n);
                        labels.add(a.unit() == null ? n : n + " " + a.unit());
                    }
                    case BOOLEAN -> {
                        values.add(String.valueOf(v.getBoolValue()));
                        labels.add(Boolean.TRUE.equals(v.getBoolValue()) ? "Yes" : "No");
                    }
                }
            }
            result.add(new ProductAttributeDto(a.code(), a.name(), a.dataType(), a.unit(), values, labels));
        }
        return result;
    }

    // ---------- reviews ----------

    public PageResponse<ReviewDto> reviews(Long productId, Short rating, Pageable pageable) {
        requireExists(productId);
        Page<Review> page = rating == null
                ? reviewRepository.findByProductId(productId, pageable)
                : reviewRepository.findByProductIdAndRating(productId, rating, pageable);
        return PageResponse.of(page, mapper::toReview);
    }

    public RatingSummary ratingSummary(Long productId) {
        requireExists(productId);
        Map<Integer, Long> distribution = new TreeMap<>(Comparator.reverseOrder());
        for (int stars = 5; stars >= 1; stars--) distribution.put(stars, 0L);
        long total = 0;
        long weighted = 0;
        for (Object[] row : reviewRepository.countByRating(productId)) {
            int stars = ((Number) row[0]).intValue();
            long count = (Long) row[1];
            distribution.put(stars, count);
            total += count;
            weighted += stars * count;
        }
        BigDecimal avg = total == 0 ? null
                : BigDecimal.valueOf(weighted).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
        return new RatingSummary(avg, total, distribution);
    }

    // ---------- helpers ----------

    private Specification<Product> spec(ProductSearchCriteria c) {
        String path = c.categoryId() == null ? null : metadata.category(c.categoryId()).path();
        return ProductSpecifications.of(c, path);
    }

    private void requireExists(Long productId) {
        if (!productRepository.existsById(productId)) throw new NotFoundException("Product", productId);
    }

    private static long nz(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }
}
