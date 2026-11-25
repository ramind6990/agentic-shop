package com.rmrx.agenticshop.app.returns.repository;

import com.rmrx.agenticshop.app.returns.entity.*;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long>, JpaSpecificationExecutor<ReturnRequest> {

    @EntityGraph(attributePaths = {"order", "orderItem", "customer"})
    List<ReturnRequest> findByOrderIdOrderByOpenedAtAsc(Long orderId);

    long countByCustomerId(Long customerId);
}
