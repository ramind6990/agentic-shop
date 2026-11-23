package com.rmrx.agenticshop.catalog.repository;

import com.rmrx.agenticshop.catalog.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    @Query("select c from Category c left join fetch c.parent order by c.level, c.sortOrder, c.name")
    List<Category> findAllOrdered();
}
