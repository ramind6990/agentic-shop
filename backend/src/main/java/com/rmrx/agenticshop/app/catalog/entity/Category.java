package com.rmrx.agenticshop.app.catalog.entity;

import com.rmrx.agenticshop.app.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Category tree node. {@code path} is a materialized path of ids (e.g. "/1/12/104/") so a whole
 * subtree can be selected with {@code path like '/1/12/%'}. Products attach to leaf categories.
 */
@Getter
@Entity
@Table(name = "category")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private short level;

    @Column(nullable = false)
    private String path;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_leaf", nullable = false)
    private boolean leaf;

    @Column(columnDefinition = "text")
    private String description;
}
