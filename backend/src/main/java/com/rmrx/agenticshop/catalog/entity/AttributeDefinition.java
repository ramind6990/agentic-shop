package com.rmrx.agenticshop.catalog.entity;

import com.rmrx.agenticshop.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** A product attribute (EAV), e.g. color, ram_gb, screen_size_in. Attached to categories via CategoryAttribute. */
@Getter
@Entity
@Table(name = "attribute_definition")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttributeDefinition extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false)
    private AttributeDataType dataType;

    private String unit;

    @Column(name = "min_value")
    private BigDecimal minValue;

    @Column(name = "max_value")
    private BigDecimal maxValue;

    @Column(name = "step_value")
    private BigDecimal stepValue;

    @BatchSize(size = 50)
    @OneToMany(mappedBy = "attribute")
    @OrderBy("sortOrder")
    private List<AttributeOption> options = new ArrayList<>();
}
