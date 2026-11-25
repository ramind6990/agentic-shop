package com.rmrx.agenticshop.app.seller.repository;

import com.rmrx.agenticshop.app.seller.entity.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayoutRepository extends JpaRepository<Payout, Long> {

    Page<Payout> findBySellerId(Long sellerId, Pageable pageable);
}
