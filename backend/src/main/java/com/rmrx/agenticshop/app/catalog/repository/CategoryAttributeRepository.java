package com.rmrx.agenticshop.app.catalog.repository;

import com.rmrx.agenticshop.app.catalog.entity.CategoryAttribute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CategoryAttributeRepository extends JpaRepository<CategoryAttribute, Long> {

    @Query("select ca from CategoryAttribute ca join fetch ca.category join fetch ca.attribute")
    List<CategoryAttribute> findAllWithAttribute();
}
