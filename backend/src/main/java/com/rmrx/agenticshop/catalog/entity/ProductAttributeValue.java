package com.rmrx.agenticshop.catalog.entity;

import com.rmrx.agenticshop.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * One attribute value of a product. Exactly one of option / numberValue / boolValue is set,
 * depending on the attribute's data type. MULTI_ENUM attributes have one row per selected option.
 */
@Getter
@Entity
@Table(name = "product_attribute_value")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductAttributeValue extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attribute_id", nullable = false)
    private AttributeDefinition attribute;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "option_id")
    private AttributeOption option;

    @Column(name = "number_value")
    private BigDecimal numberValue;

    @Column(name = "bool_value")
    private Boolean boolValue;
}
