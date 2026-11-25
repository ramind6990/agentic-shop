package com.rmrx.agenticshop.app.catalog.repository;

import com.rmrx.agenticshop.app.catalog.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
}
