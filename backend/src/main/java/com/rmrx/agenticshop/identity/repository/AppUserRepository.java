package com.rmrx.agenticshop.identity.repository;

import com.rmrx.agenticshop.identity.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long>, JpaSpecificationExecutor<AppUser> {

    @EntityGraph(attributePaths = "seller")
    Optional<AppUser> findByUsername(String username);
}
