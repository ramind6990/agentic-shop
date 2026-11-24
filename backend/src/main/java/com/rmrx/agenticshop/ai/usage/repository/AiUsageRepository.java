package com.rmrx.agenticshop.ai.usage.repository;

import com.rmrx.agenticshop.ai.usage.entity.AiUsage;
import com.rmrx.agenticshop.ai.usage.entity.AiUsagePurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiUsageRepository extends JpaRepository<AiUsage, Long> {

    @Query("""
            select new com.rmrx.agenticshop.ai.usage.repository.UsageTotals(
                count(u), coalesce(sum(u.inputTokens), 0L), coalesce(sum(u.cachedInputTokens), 0L),
                coalesce(sum(u.outputTokens), 0L), coalesce(sum(u.costUsd), 0bd))
            from AiUsage u where u.ticketId = :ticketId""")
    UsageTotals sumByTicket(@Param("ticketId") Long ticketId);

    /** Usage for a user in [from, to). */
    @Query("""
            select new com.rmrx.agenticshop.ai.usage.repository.UsageTotals(
                count(u), coalesce(sum(u.inputTokens), 0L), coalesce(sum(u.cachedInputTokens), 0L),
                coalesce(sum(u.outputTokens), 0L), coalesce(sum(u.costUsd), 0bd))
            from AiUsage u
            where u.userId = :userId and u.createdAt >= :from and u.createdAt < :to""")
    UsageTotals sumByUserBetween(@Param("userId") Long userId, @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select new com.rmrx.agenticshop.ai.usage.repository.ModelUsageTotals(
                u.provider, u.model, count(u), sum(u.inputTokens), sum(u.cachedInputTokens),
                sum(u.outputTokens), sum(u.costUsd))
            from AiUsage u where u.userId = :userId
            group by u.provider, u.model
            order by sum(u.costUsd) desc""")
    List<ModelUsageTotals> sumByModelForUser(@Param("userId") Long userId);

    /** Per-model usage for a user in [from, to), most expensive first. */
    @Query("""
            select new com.rmrx.agenticshop.ai.usage.repository.ModelUsageTotals(
                u.provider, u.model, count(u), sum(u.inputTokens), sum(u.cachedInputTokens),
                sum(u.outputTokens), sum(u.costUsd))
            from AiUsage u
            where u.userId = :userId and u.createdAt >= :from and u.createdAt < :to
            group by u.provider, u.model
            order by sum(u.costUsd) desc""")
    List<ModelUsageTotals> sumByModelForUserBetween(@Param("userId") Long userId, @Param("from") Instant from,
                                                    @Param("to") Instant to);

    /** The latest row of a ticket for a purpose; for CHAT its input tokens are the current context size. */
    Optional<AiUsage> findFirstByTicketIdAndPurposeOrderByCreatedAtDescIdDesc(Long ticketId, AiUsagePurpose purpose);

    List<AiUsage> findByRunIdOrderByIdAsc(UUID runId);
}
