package com.rmrx.agenticshop.identity.repository;

import com.rmrx.agenticshop.identity.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AddressRepository extends JpaRepository<Address, Long> {

    List<Address> findByUserIdOrderByDefaultAddressDescIdAsc(Long userId);
}
