package com.rmrx.agenticshop.order.repository;

import com.rmrx.agenticshop.order.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
}
