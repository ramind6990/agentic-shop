package com.rmrx.agenticshop.docs.repository;

import com.rmrx.agenticshop.docs.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentSectionRepository extends JpaRepository<DocumentSection, Long> {

    List<DocumentSection> findByVersionIdOrderByOrdinalAsc(Long versionId);
}
