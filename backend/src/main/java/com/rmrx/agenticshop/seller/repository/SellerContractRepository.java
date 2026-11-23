package com.rmrx.agenticshop.seller.repository;

import com.rmrx.agenticshop.seller.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SellerContractRepository extends JpaRepository<SellerContract, Long> {

    List<SellerContract> findBySellerIdOrderByEffectiveFromDesc(Long sellerId);
}
