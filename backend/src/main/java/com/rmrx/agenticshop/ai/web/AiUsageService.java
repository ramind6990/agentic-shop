package com.rmrx.agenticshop.ai.web;

import com.rmrx.agenticshop.ai.trace.entity.AiRun;
import com.rmrx.agenticshop.ai.trace.repository.AiRunRepository;
import com.rmrx.agenticshop.ai.trace.repository.AiStepRepository;
import com.rmrx.agenticshop.ai.usage.entity.AiUsage;
import com.rmrx.agenticshop.ai.usage.entity.AiUsagePurpose;
import com.rmrx.agenticshop.ai.usage.repository.AiUsageRepository;
import com.rmrx.agenticshop.ai.web.dto.AiUsageDtos.*;
import com.rmrx.agenticshop.common.exception.BadRequestException;
import com.rmrx.agenticshop.common.exception.NotFoundException;
import com.rmrx.agenticshop.security.AccessScope;
import com.rmrx.agenticshop.support.entity.Ticket;
import com.rmrx.agenticshop.support.repository.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/** Read side of the usage ledger and run traces. */
@Service
@Transactional(readOnly = true)
public class AiUsageService {

    private final AiUsageRepository usages;
    private final AiRunRepository runs;
    private final AiStepRepository steps;
    private final TicketRepository tickets;
    private final AccessScope scope;

    public AiUsageService(AiUsageRepository usages, AiRunRepository runs, AiStepRepository steps,
                          TicketRepository tickets, AccessScope scope) {
        this.usages = usages;
        this.runs = runs;
        this.steps = steps;
        this.tickets = tickets;
        this.scope = scope;
    }

    public UserUsage myUsage(LocalDate from, LocalDate to) {
        return userUsage(scope.current().id(), from, to);
    }

    /**
     * Usage of a user over the UTC days from..to, both inclusive. Without dates: the current calendar month (UTC).
     */
    public UserUsage userUsage(Long userId, LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate start = from != null ? from : today.withDayOfMonth(1);
        LocalDate endExclusive = to != null ? to.plusDays(1) : today.withDayOfMonth(1).plusMonths(1);
        if (!start.isBefore(endExclusive)) throw new BadRequestException("'from' must not be after 'to'");
        Instant fromAt = start.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toAt = endExclusive.atStartOfDay(ZoneOffset.UTC).toInstant();
        return new UserUsage(userId, fromAt, toAt,
                Totals.of(usages.sumByUserBetween(userId, fromAt, toAt)),
                usages.sumByModelForUserBetween(userId, fromAt, toAt).stream().map(ModelTotals::of).toList());
    }

    /** Lifetime usage of a ticket the caller can see; 404 otherwise. */
    public TicketUsage ticketUsage(Long ticketId) {
        Ticket ticket = tickets.findById(ticketId).orElseThrow(() -> new NotFoundException("Ticket", ticketId));
        scope.requireOwned(ticket.getCustomer().getId(),
                ticket.getSeller() == null ? null : ticket.getSeller().getId(), "Ticket", ticketId);
        long context = usages.findFirstByTicketIdAndPurposeOrderByCreatedAtDescIdDesc(ticketId, AiUsagePurpose.CHAT)
                .map(AiUsage::getInputTokens).orElse(0);
        return new TicketUsage(ticketId, Totals.of(usages.sumByTicket(ticketId)), context);
    }

    public RunTrace trace(UUID runId) {
        AiRun run = runs.findById(runId).orElseThrow(() -> new NotFoundException("Run", runId));
        return RunTrace.of(run, steps.findByRunIdOrderBySeqAsc(runId));
    }
}
