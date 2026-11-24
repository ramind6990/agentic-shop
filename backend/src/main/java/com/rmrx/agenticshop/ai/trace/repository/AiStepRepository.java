package com.rmrx.agenticshop.ai.trace.repository;

import com.rmrx.agenticshop.ai.trace.entity.AiStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AiStepRepository extends JpaRepository<AiStep, Long> {

    List<AiStep> findByRunIdOrderBySeqAsc(UUID runId);
}
