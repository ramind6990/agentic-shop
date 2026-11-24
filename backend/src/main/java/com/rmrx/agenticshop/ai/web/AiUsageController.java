package com.rmrx.agenticshop.ai.web;

import com.rmrx.agenticshop.ai.web.dto.AiUsageDtos.RunTrace;
import com.rmrx.agenticshop.ai.web.dto.AiUsageDtos.TicketUsage;
import com.rmrx.agenticshop.ai.web.dto.AiUsageDtos.UserUsage;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * AI token usage and run traces. Periods are UTC days, from and to inclusive (ISO dates); without them the
 * current calendar month.
 */
@RestController
@RequestMapping("/api/ai")
public class AiUsageController {

    private final AiUsageService service;

    public AiUsageController(AiUsageService service) {
        this.service = service;
    }

    @GetMapping("/usage/me")
    @PreAuthorize("hasAuthority('AI_USAGE_READ_OWN')")
    public UserUsage myUsage(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.myUsage(from, to);
    }

    @GetMapping("/usage/users/{userId}")
    @PreAuthorize("hasAuthority('AI_USAGE_READ_ALL')")
    public UserUsage userUsage(@PathVariable Long userId,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.userUsage(userId, from, to);
    }

    /** Usage of a ticket the caller can see (404 otherwise), with the current context size. */
    @GetMapping("/tickets/{ticketId}/usage")
    @PreAuthorize("hasAuthority('AI_USAGE_READ_OWN')")
    public TicketUsage ticketUsage(@PathVariable Long ticketId) {
        return service.ticketUsage(ticketId);
    }

    @GetMapping("/runs/{runId}/trace")
    @PreAuthorize("hasAuthority('AI_TRACE_READ')")
    public RunTrace trace(@PathVariable UUID runId) {
        return service.trace(runId);
    }
}
