package com.rmrx.agenticshop.ai.trace.repository;

import com.rmrx.agenticshop.ai.trace.entity.AiRun;
import com.rmrx.agenticshop.ai.trace.entity.AiRunStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiRunRepository extends JpaRepository<AiRun, UUID> {

    List<AiRun> findByTicketIdOrderByStartedAtDesc(Long ticketId);

    /** Runs in a status, oldest first (startup recovery looks for RUNNING ones). */
    List<AiRun> findByStatusOrderByStartedAtAsc(AiRunStatus status);

    Page<AiRun> findByUserIdOrderByStartedAtDesc(Long userId, Pageable pageable);

    /** The run row locked for update, so concurrent steps of one run get distinct seq values. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AiRun r where r.id = :id")
    Optional<AiRun> findForUpdate(@Param("id") UUID id);
}
