package com.rmrx.agenticshop.app.docs.repository;

import com.rmrx.agenticshop.app.docs.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {

    List<DocumentVersion> findByDocumentIdOrderByVersionNoAsc(Long documentId);

    Optional<DocumentVersion> findByDocumentIdAndVersionNo(Long documentId, int versionNo);

    /** The version in force on the given date: effectiveFrom <= at < effectiveTo (open-ended when null). */
    @Query("""
            select v from DocumentVersion v
            where v.document.id = :documentId
              and v.effectiveFrom <= :at
              and (v.effectiveTo is null or v.effectiveTo > :at)
            """)
    Optional<DocumentVersion> findEffective(Long documentId, LocalDate at);
}
