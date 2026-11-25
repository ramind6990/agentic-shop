package com.rmrx.agenticshop.app.docs.repository;

import com.rmrx.agenticshop.app.docs.entity.*;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long>, JpaSpecificationExecutor<Document> {

    @EntityGraph(attributePaths = "seller")
    Optional<Document> findByCode(String code);
}
