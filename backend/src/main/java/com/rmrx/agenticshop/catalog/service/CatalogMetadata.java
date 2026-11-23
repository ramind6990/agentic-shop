package com.rmrx.agenticshop.catalog.service;

import com.rmrx.agenticshop.catalog.entity.AttributeDataType;
import com.rmrx.agenticshop.catalog.entity.AttributeDefinition;
import com.rmrx.agenticshop.catalog.entity.Category;
import com.rmrx.agenticshop.catalog.entity.CategoryAttribute;
import com.rmrx.agenticshop.catalog.repository.AttributeDefinitionRepository;
import com.rmrx.agenticshop.catalog.repository.CategoryAttributeRepository;
import com.rmrx.agenticshop.catalog.repository.CategoryRepository;
import com.rmrx.agenticshop.common.exception.NotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.*;

/**
 * In-memory snapshot of the (small, rarely changing) catalog structure: category tree, attribute definitions
 * and which attributes each category exposes. Loaded lazily on first use; products are NOT cached here.
 */
@Component
public class CatalogMetadata {

    public record CategoryInfo(Long id, Long parentId, String name, String slug, short level, String path,
                               int sortOrder, boolean leaf, String description) {
    }

    public record OptionInfo(Long id, String value, String label, int sortOrder) {
    }

    public record AttributeInfo(Long id, String code, String name, AttributeDataType dataType, String unit,
                                BigDecimal minValue, BigDecimal maxValue, List<OptionInfo> options) {

        public Optional<OptionInfo> option(String value) {
            return options.stream().filter(o -> o.value().equalsIgnoreCase(value)).findFirst();
        }

        public Optional<OptionInfo> option(Long optionId) {
            return options.stream().filter(o -> o.id().equals(optionId)).findFirst();
        }
    }

    public record CategoryAttributeInfo(AttributeInfo attribute, boolean filterable, int sortOrder, Long definedOn) {
    }

    private record Snapshot(Map<Long, CategoryInfo> categories,
                            Map<String, CategoryInfo> categoriesBySlug,
                            Map<Long, List<CategoryInfo>> children,
                            Map<Long, AttributeInfo> attributes,
                            Map<String, AttributeInfo> attributesByCode,
                            Map<Long, List<CategoryAttributeInfo>> directCategoryAttributes) {
    }

    private final CategoryRepository categoryRepository;
    private final AttributeDefinitionRepository attributeRepository;
    private final CategoryAttributeRepository categoryAttributeRepository;
    private final TransactionTemplate tx;
    private volatile Snapshot snapshot;

    public CatalogMetadata(CategoryRepository categoryRepository,
                           AttributeDefinitionRepository attributeRepository,
                           CategoryAttributeRepository categoryAttributeRepository,
                           TransactionTemplate tx) {
        this.categoryRepository = categoryRepository;
        this.attributeRepository = attributeRepository;
        this.categoryAttributeRepository = categoryAttributeRepository;
        this.tx = tx;
    }

    // ---------- categories ----------

    public List<CategoryInfo> roots() {
        return snapshot().children().getOrDefault(0L, List.of());
    }

    public List<CategoryInfo> children(Long categoryId) {
        return snapshot().children().getOrDefault(categoryId, List.of());
    }

    public Optional<CategoryInfo> findCategory(Long id) {
        return Optional.ofNullable(snapshot().categories().get(id));
    }

    public CategoryInfo category(Long id) {
        return findCategory(id).orElseThrow(() -> new NotFoundException("Category", id));
    }

    public CategoryInfo categoryByIdOrSlug(String idOrSlug) {
        Snapshot s = snapshot();
        CategoryInfo c = idOrSlug.chars().allMatch(Character::isDigit)
                ? s.categories().get(Long.parseLong(idOrSlug))
                : s.categoriesBySlug().get(idOrSlug);
        if (c == null) throw new NotFoundException("Category", idOrSlug);
        return c;
    }

    /** Root first, the category itself last. */
    public List<CategoryInfo> ancestry(Long categoryId) {
        LinkedList<CategoryInfo> chain = new LinkedList<>();
        CategoryInfo current = category(categoryId);
        while (current != null) {
            chain.addFirst(current);
            current = current.parentId() == null ? null : snapshot().categories().get(current.parentId());
        }
        return chain;
    }

    // ---------- attributes ----------

    public Optional<AttributeInfo> attributeByCode(String code) {
        return Optional.ofNullable(snapshot().attributesByCode().get(code));
    }

    public AttributeInfo attribute(Long id) {
        return snapshot().attributes().get(id);
    }

    /** Attributes of a category including those inherited from its ancestors, ordered root-first then by sortOrder. */
    public List<CategoryAttributeInfo> effectiveAttributes(Long categoryId) {
        List<CategoryAttributeInfo> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (CategoryInfo c : ancestry(categoryId)) {
            for (CategoryAttributeInfo ca : snapshot().directCategoryAttributes().getOrDefault(c.id(), List.of())) {
                if (seen.add(ca.attribute().id())) result.add(ca);
            }
        }
        return result;
    }

    public void refresh() {
        snapshot = null;
    }

    private Snapshot snapshot() {
        Snapshot s = snapshot;
        if (s == null) {
            synchronized (this) {
                s = snapshot;
                if (s == null) {
                    s = tx.execute(status -> load());
                    snapshot = s;
                }
            }
        }
        return s;
    }

    private Snapshot load() {
        Map<Long, CategoryInfo> categories = new LinkedHashMap<>();
        Map<String, CategoryInfo> bySlug = new HashMap<>();
        Map<Long, List<CategoryInfo>> children = new HashMap<>();
        for (Category c : categoryRepository.findAllOrdered()) {
            Long parentId = c.getParent() == null ? null : c.getParent().getId();
            CategoryInfo info = new CategoryInfo(c.getId(), parentId, c.getName(), c.getSlug(), c.getLevel(),
                    c.getPath(), c.getSortOrder(), c.isLeaf(), c.getDescription());
            categories.put(info.id(), info);
            bySlug.put(info.slug(), info);
            children.computeIfAbsent(parentId == null ? 0L : parentId, k -> new ArrayList<>()).add(info);
        }

        Map<Long, AttributeInfo> attributes = new HashMap<>();
        Map<String, AttributeInfo> byCode = new HashMap<>();
        for (AttributeDefinition a : attributeRepository.findAllWithOptions()) {
            List<OptionInfo> options = a.getOptions().stream()
                    .map(o -> new OptionInfo(o.getId(), o.getValue(), o.getLabel(), o.getSortOrder()))
                    .toList();
            AttributeInfo info = new AttributeInfo(a.getId(), a.getCode(), a.getName(), a.getDataType(),
                    a.getUnit(), a.getMinValue(), a.getMaxValue(), options);
            attributes.put(info.id(), info);
            byCode.put(info.code(), info);
        }

        Map<Long, List<CategoryAttributeInfo>> direct = new HashMap<>();
        for (CategoryAttribute ca : categoryAttributeRepository.findAllWithAttribute()) {
            direct.computeIfAbsent(ca.getCategory().getId(), k -> new ArrayList<>())
                    .add(new CategoryAttributeInfo(attributes.get(ca.getAttribute().getId()), ca.isFilterable(),
                            ca.getSortOrder(), ca.getCategory().getId()));
        }
        direct.values().forEach(list -> list.sort(Comparator.comparingInt(CategoryAttributeInfo::sortOrder)));

        return new Snapshot(categories, bySlug, children, attributes, byCode, direct);
    }
}
