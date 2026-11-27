package com.rmrx.agenticshop.app.order.repository;

import com.rmrx.agenticshop.app.order.entity.*;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ShipmentRepository extends JpaRepository<Shipment, Long>, JpaSpecificationExecutor<Shipment> {

    List<Shipment> findByOrderIdOrderByIdAsc(Long orderId);

    List<Shipment> findByOrderIdInOrderByIdAsc(Collection<Long> orderIds);

    @EntityGraph(attributePaths = {"order", "order.customer", "order.seller"})
    Optional<Shipment> findFirstByTrackingNumberIgnoreCase(String trackingNumber);
}
