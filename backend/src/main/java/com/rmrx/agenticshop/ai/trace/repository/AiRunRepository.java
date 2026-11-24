package com.rmrx.agenticshop.ai.trace.repository;

import com.rmrx.agenticshop.ai.trace.entity.AiRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AiRunRepository extends JpaRepository<AiRun, UUID> {

    List<AiRun> findByTicketIdOrderByStartedAtDesc(Long ticketId);

    Page<AiRun> findByUserIdOrderByStartedAtDesc(Long userId, Pageable pageable);
}
