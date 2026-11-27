package com.rmrx.agenticshop.ai.web;

import com.rmrx.agenticshop.ai.trace.TraceQueries;
import com.rmrx.agenticshop.ai.trace.TraceViews.RunSummary;
import com.rmrx.agenticshop.ai.trace.TraceViews.RunTrace;
import com.rmrx.agenticshop.ai.usage.UsageService;
import com.rmrx.agenticshop.ai.usage.UsageViews.ConversationUsage;
import com.rmrx.agenticshop.ai.usage.UsageViews.UserUsage;
import com.rmrx.agenticshop.app.security.AccessScope;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Token usage of the ai agent, and run traces. Usage is in tokens, per issuer (USER: answering the user, SYSTEM: the
 * system's own work such as summarizing the memory). Periods are UTC days, from and to inclusive (ISO dates); without
 * them the current calendar month.
 */
@RestController("aiUsageController")
@RequestMapping("/api/ai")
public class UsageController {

    private final UsageService usage;
    private final TraceQueries traces;
    private final AccessScope scope;

    public UsageController(UsageService usage, TraceQueries traces, AccessScope scope) {
        this.usage = usage;
        this.traces = traces;
        this.scope = scope;
    }

    @GetMapping("/usage/me")
    @PreAuthorize("hasAuthority('AI_USAGE_READ_OWN')")
    public UserUsage myUsage(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return usage.userUsage(scope.current().id(), from, to);
    }

    @GetMapping("/usage/users/{userId}")
    @PreAuthorize("hasAuthority('AI_USAGE_READ_ALL')")
    public UserUsage userUsage(@PathVariable Long userId,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return usage.userUsage(userId, from, to);
    }

    /** The caller's conversation: tokens per issuer and its context (latest call input, memory); 404 if not theirs. */
    @GetMapping("/conversations/{conversationId}/usage")
    @PreAuthorize("hasAuthority('AI_CHAT')")
    public ConversationUsage conversationUsage(@PathVariable Long conversationId) {
        return usage.conversationUsage(scope.current().id(), conversationId);
    }

    /** A conversation's runs, newest first (staff debugging). */
    @GetMapping("/conversations/{conversationId}/runs")
    @PreAuthorize("hasAuthority('AI_TRACE_READ')")
    public List<RunSummary> runs(@PathVariable Long conversationId) {
        return traces.runs(conversationId);
    }

    /** One run with every step: input, model attempts, tools, validation, pause, reply or error. */
    @GetMapping("/runs/{runId}/trace")
    @PreAuthorize("hasAuthority('AI_TRACE_READ')")
    public RunTrace trace(@PathVariable UUID runId) {
        return traces.trace(runId);
    }
}
