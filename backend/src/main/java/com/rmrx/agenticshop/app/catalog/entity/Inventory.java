package com.rmrx.agenticshop.app.catalog.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** Stock level of a product (shares the product's primary key). */
@Getter
@Entity
@Table(name = "inventory")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inventory {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "qty_available", nullable = false)
    private int qtyAvailable;

    @Column(name = "qty_reserved", nullable = false)
    private int qtyReserved;

    @Column(name = "warehouse_code", nullable = false)
    private String warehouseCode;

    @Column(name = "restock_eta")
    private LocalDate restockEta;

    public boolean isInStock() {
        return qtyAvailable - qtyReserved > 0;
    }

    /** Gives reserved units back to stock (never below zero reserved). */
    public void release(int quantity) {
        this.qtyReserved = Math.max(0, qtyReserved - quantity);
    }
}
