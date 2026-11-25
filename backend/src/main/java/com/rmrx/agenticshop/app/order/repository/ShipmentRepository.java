package com.rmrx.agenticshop.app.order.repository;

import com.rmrx.agenticshop.app.order.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    List<Shipment> findByOrderIdOrderByIdAsc(Long orderId);
}
