package com.rmrx.agenticshop.app.catalog.repository;

import com.rmrx.agenticshop.app.catalog.entity.ProductAttributeValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProductAttributeValueRepository extends JpaRepository<ProductAttributeValue, Long> {

    @Query("""
            select v from ProductAttributeValue v
              join fetch v.attribute
              left join fetch v.option
            where v.product.id = :productId
            """)
    List<ProductAttributeValue> findByProductId(Long productId);
}
