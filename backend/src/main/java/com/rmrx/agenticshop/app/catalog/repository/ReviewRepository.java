package com.rmrx.agenticshop.app.catalog.repository;

import com.rmrx.agenticshop.app.catalog.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    @EntityGraph(attributePaths = "customer")
    Page<Review> findByProductId(Long productId, Pageable pageable);

    @EntityGraph(attributePaths = "customer")
    Page<Review> findByProductIdAndRating(Long productId, short rating, Pageable pageable);

    @EntityGraph(attributePaths = "product")
    Page<Review> findByCustomerId(Long customerId, Pageable pageable);

    /** Rows of [rating, count] for the product's star distribution. */
    @Query("select r.rating, count(r) from Review r where r.product.id = :productId group by r.rating")
    List<Object[]> countByRating(Long productId);
}
