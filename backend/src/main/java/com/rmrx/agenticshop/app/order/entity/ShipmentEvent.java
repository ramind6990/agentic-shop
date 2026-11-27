package com.rmrx.agenticshop.app.order.entity;

import com.rmrx.agenticshop.app.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** One carrier scan of a shipment. */
@Getter
@Entity
@Table(name = "shipment_event")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShipmentEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false)
    private Shipment shipment;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShipmentEventType type;

    private String location;

    @Column(nullable = false)
    private String description;
}
