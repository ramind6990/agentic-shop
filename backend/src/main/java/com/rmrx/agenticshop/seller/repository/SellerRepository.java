package com.rmrx.agenticshop.seller.repository;

import com.rmrx.agenticshop.seller.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface SellerRepository extends JpaRepository<Seller, Long>, JpaSpecificationExecutor<Seller> {

    Optional<Seller> findBySlug(String slug);
}
