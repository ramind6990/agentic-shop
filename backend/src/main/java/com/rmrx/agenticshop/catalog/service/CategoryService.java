package com.rmrx.agenticshop.catalog.service;

import com.rmrx.agenticshop.catalog.dto.CategoryDtos.*;
import com.rmrx.agenticshop.catalog.service.CatalogMetadata.CategoryInfo;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryService {

    private final CatalogMetadata metadata;

    public CategoryService(CatalogMetadata metadata) {
        this.metadata = metadata;
    }

    public List<CategoryNode> tree() {
        return metadata.roots().stream().map(this::node).toList();
    }

    public CategoryDetail get(String idOrSlug) {
        CategoryInfo c = metadata.categoryByIdOrSlug(idOrSlug);
        return new CategoryDetail(c.id(), c.name(), c.slug(), c.level(), c.leaf(), c.description(),
                metadata.ancestry(c.id()).stream().map(CategoryService::ref).toList(),
                metadata.children(c.id()).stream().map(CategoryService::ref).toList());
    }

    /** Filterable attributes of the category, including those inherited from ancestor categories. */
    public List<AttributeFilter> filters(String idOrSlug) {
        CategoryInfo c = metadata.categoryByIdOrSlug(idOrSlug);
        return metadata.effectiveAttributes(c.id()).stream()
                .filter(CatalogMetadata.CategoryAttributeInfo::filterable)
                .map(ca -> {
                    var a = ca.attribute();
                    return new AttributeFilter(a.code(), a.name(), a.dataType(), a.unit(), a.minValue(),
                            a.maxValue(),
                            a.options().stream().map(o -> new AttributeOptionDto(o.value(), o.label())).toList());
                })
                .toList();
    }

    static CategoryRef ref(CategoryInfo c) {
        return new CategoryRef(c.id(), c.name(), c.slug());
    }

    private CategoryNode node(CategoryInfo c) {
        return new CategoryNode(c.id(), c.name(), c.slug(), c.level(), c.leaf(),
                metadata.children(c.id()).stream().map(this::node).toList());
    }
}
