package com.rmrx.agenticshop.order.repository;

import com.rmrx.agenticshop.order.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByOrderIdOrderByCreatedAtAsc(Long orderId);
}
