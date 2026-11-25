package com.rmrx.agenticshop.app.returns.repository;

import com.rmrx.agenticshop.app.returns.entity.*;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface RefundRepository extends JpaRepository<Refund, Long>, JpaSpecificationExecutor<Refund> {

    @EntityGraph(attributePaths = {"order", "issuedBy"})
    List<Refund> findByOrderIdOrderByCreatedAtAsc(Long orderId);
}
