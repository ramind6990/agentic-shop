package com.rmrx.agenticshop.app.seller.repository;

import com.rmrx.agenticshop.app.seller.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SellerContractRepository extends JpaRepository<SellerContract, Long> {

    List<SellerContract> findBySellerIdOrderByEffectiveFromDesc(Long sellerId);
}
