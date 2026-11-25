package com.rmrx.agenticshop.app.catalog.repository;

import com.rmrx.agenticshop.app.catalog.entity.Product;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product>,
        ProductFacetRepository {

    @EntityGraph(attributePaths = {"seller", "category", "brand"})
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findDetailedById(Long id);

    long countBySellerId(Long sellerId);
}
