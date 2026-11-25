package com.rmrx.agenticshop.app.catalog.repository;

import com.rmrx.agenticshop.app.catalog.entity.Brand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BrandRepository extends JpaRepository<Brand, Long> {

    Page<Brand> findByNameContainingIgnoreCase(String name, Pageable pageable);

    /** Brands that have at least one active product in the category subtree identified by its path prefix. */
    @Query(value = """
            select b from Brand b
            where lower(b.name) like lower(concat('%', :q, '%'))
              and exists (select 1 from Product p
                          where p.brand = b and p.status = com.rmrx.agenticshop.catalog.entity.ProductStatus.ACTIVE
                            and p.category.path like concat(:path, '%'))
            """)
    Page<Brand> findInCategory(@Param("q") String q, @Param("path") String categoryPath, Pageable pageable);
}
