package com.rmrx.agenticshop.ai.usage.repository;

import com.rmrx.agenticshop.ai.usage.entity.AiUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

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
}
