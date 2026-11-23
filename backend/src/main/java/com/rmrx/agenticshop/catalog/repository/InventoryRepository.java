package com.rmrx.agenticshop.catalog.repository;

import com.rmrx.agenticshop.catalog.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
}
