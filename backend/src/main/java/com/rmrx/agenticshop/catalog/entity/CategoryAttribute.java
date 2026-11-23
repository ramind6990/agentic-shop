package com.rmrx.agenticshop.catalog.entity;

import com.rmrx.agenticshop.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Attaches an attribute to a category. Descendant categories inherit it. */
@Getter
@Entity
@Table(name = "category_attribute")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CategoryAttribute extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attribute_id", nullable = false)
    private AttributeDefinition attribute;

    @Column(nullable = false)
    private boolean filterable;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
