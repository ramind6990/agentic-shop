package com.rmrx.agenticshop.app.catalog.entity;

import com.rmrx.agenticshop.app.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** An allowed value of an ENUM / MULTI_ENUM attribute, e.g. color = "black". */
@Getter
@Entity
@Table(name = "attribute_option")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttributeOption extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attribute_id", nullable = false)
    private AttributeDefinition attribute;

    @Column(nullable = false)
    private String value;

    @Column(nullable = false)
    private String label;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
