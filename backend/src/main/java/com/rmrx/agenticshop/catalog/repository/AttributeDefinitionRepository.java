package com.rmrx.agenticshop.catalog.repository;

import com.rmrx.agenticshop.catalog.entity.AttributeDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AttributeDefinitionRepository extends JpaRepository<AttributeDefinition, Long> {

    @Query("select distinct a from AttributeDefinition a left join fetch a.options order by a.code")
    List<AttributeDefinition> findAllWithOptions();
}
