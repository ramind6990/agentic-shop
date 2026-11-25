package com.rmrx.agenticshop.app.order.repository;

import com.rmrx.agenticshop.app.order.entity.*;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long>, JpaSpecificationExecutor<CustomerOrder> {

    @EntityGraph(attributePaths = {"customer", "seller"})
    Optional<CustomerOrder> findByOrderNumber(String orderNumber);

    @EntityGraph(attributePaths = {"customer", "seller"})
    @Query("select o from CustomerOrder o where o.id = :id")
    Optional<CustomerOrder> findDetailedById(Long id);

    long countBySellerId(Long sellerId);

    long countByCustomerId(Long customerId);
}
