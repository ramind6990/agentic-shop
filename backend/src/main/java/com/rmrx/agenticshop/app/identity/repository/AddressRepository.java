package com.rmrx.agenticshop.app.identity.repository;

import com.rmrx.agenticshop.app.identity.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AddressRepository extends JpaRepository<Address, Long> {

    List<Address> findByUserIdOrderByDefaultAddressDescIdAsc(Long userId);
}
