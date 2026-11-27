package com.rmrx.agenticshop.app.order.repository;

import com.rmrx.agenticshop.app.order.entity.ShipmentEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ShipmentEventRepository extends JpaRepository<ShipmentEvent, Long> {

    List<ShipmentEvent> findByShipmentIdInOrderByOccurredAtAsc(Collection<Long> shipmentIds);
}
