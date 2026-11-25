package com.rmrx.agenticshop.app.order.repository;

import com.rmrx.agenticshop.app.order.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
}
