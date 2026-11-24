package com.rmrx.agenticshop.ai.action.repository;

import com.rmrx.agenticshop.ai.action.entity.ActionOutcome;
import com.rmrx.agenticshop.ai.action.entity.ActionTargetType;
import com.rmrx.agenticshop.ai.action.entity.AiAction;
import com.rmrx.agenticshop.ai.contract.response.ActionState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AiActionRepository extends JpaRepository<AiAction, Long> {

    Optional<AiAction> findByTokenHash(String tokenHash);

    List<AiAction> findByTokenHashIn(Collection<String> tokenHashes);

    List<AiAction> findByTargetTypeAndTargetIdAndState(ActionTargetType targetType, Long targetId, ActionState state);

    default List<AiAction> findOpenByTarget(ActionTargetType targetType, Long targetId) {
        return findByTargetTypeAndTargetIdAndState(targetType, targetId, ActionState.OPEN);
    }

    List<AiAction> findByTicketIdAndState(Long ticketId, ActionState state);

    List<AiAction> findByTicketIdOrderByIdAsc(Long ticketId);

    /** The CONFIRM/DECLINE rows of one pending write, linked by the {@code pending} id in their params. */
    @Query(value = "select * from ai_action where ticket_id = :ticketId and params ->> 'pending' = :pending",
            nativeQuery = true)
    List<AiAction> findByPending(@Param("ticketId") Long ticketId, @Param("pending") String pending);

    /**
     * Uses one action atomically: OPEN and unexpired to USED with an outcome. Returns 0 if someone else used,
     * superseded or expired it first, so a token can never be used twice.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AiAction a set a.state = com.rmrx.agenticshop.ai.contract.response.ActionState.USED,
                   a.outcome = :outcome, a.outcomeDetail = :detail, a.usedAt = :now
            where a.id = :id and a.state = com.rmrx.agenticshop.ai.contract.response.ActionState.OPEN
              and a.expiresAt > :now""")
    int claim(@Param("id") Long id, @Param("outcome") ActionOutcome outcome, @Param("detail") String detail,
              @Param("now") Instant now);

    /** Marks the still-OPEN rows among ids as USED with an outcome (the siblings of a used confirmation). */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AiAction a set a.state = com.rmrx.agenticshop.ai.contract.response.ActionState.USED,
                   a.outcome = :outcome, a.outcomeDetail = :detail, a.usedAt = :now
            where a.id in :ids and a.state = com.rmrx.agenticshop.ai.contract.response.ActionState.OPEN""")
    int useOpen(@Param("ids") Collection<Long> ids, @Param("outcome") ActionOutcome outcome,
                @Param("detail") String detail, @Param("now") Instant now);

    /** Sets the final outcome of an already used action (after its write ran). */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AiAction a set a.outcome = :outcome, a.outcomeDetail = :detail where a.id = :id")
    int recordOutcome(@Param("id") Long id, @Param("outcome") ActionOutcome outcome, @Param("detail") String detail);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AiAction a set a.messageId = :messageId where a.id in :ids and a.messageId is null")
    int attachToMessage(@Param("ids") Collection<Long> ids, @Param("messageId") Long messageId);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AiAction a set a.state = com.rmrx.agenticshop.ai.contract.response.ActionState.EXPIRED
            where a.state = com.rmrx.agenticshop.ai.contract.response.ActionState.OPEN and a.expiresAt <= :now""")
    int expireAllBefore(@Param("now") Instant now);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AiAction a set a.state = com.rmrx.agenticshop.ai.contract.response.ActionState.EXPIRED
            where a.ticketId = :ticketId
              and a.state = com.rmrx.agenticshop.ai.contract.response.ActionState.OPEN and a.expiresAt <= :now""")
    int expireTicketBefore(@Param("ticketId") Long ticketId, @Param("now") Instant now);
}
