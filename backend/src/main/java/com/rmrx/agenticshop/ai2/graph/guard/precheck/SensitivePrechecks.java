package com.rmrx.agenticshop.ai2.graph.guard.precheck;

import com.rmrx.agenticshop.ai2.graph.guard.GuardTrace;
import com.rmrx.agenticshop.ai2.graph.state.RunContext;
import com.rmrx.agenticshop.app.security.AccessScope;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Guardrail "pre-approval check": before the user is asked to approve a sensitive call, the shop's rules are asked
 * whether it could run at all, for the run's user. A call that could not (an order already shipped, a refund above the
 * caller's limit, a colleague's request the caller may not decide) gets an error result right away, so the user is
 * never asked to approve something that can only fail. An allowed call may get a note for its approval detail.
 * Tools without a precheck are allowed as they are.
 */
@Component
public class SensitivePrechecks {

    static final String BLOCKED_PREFIX = "Not executed (checked before asking for approval): ";

    private final Map<String, SensitivePrecheck> prechecks;
    private final AccessScope scope;
    private final GuardTrace trace;

    public SensitivePrechecks(List<SensitivePrecheck> prechecks, AccessScope scope, GuardTrace trace) {
        this.prechecks = prechecks.stream().collect(Collectors.toMap(SensitivePrecheck::toolName,
                Function.identity()));
        this.scope = scope;
        this.trace = trace;
    }

    public PrecheckOutcome check(RunContext context, List<ToolExecutionRequest> calls) {
        List<String> allowed = new ArrayList<>();
        List<ToolExecutionResultMessage> blocked = new ArrayList<>();
        Map<String, String> notes = new HashMap<>();
        for (ToolExecutionRequest call : calls) {
            switch (run(context, call)) {
                case PrecheckResult.Allowed(String note) -> {
                    allowed.add(call.id());
                    if (note != null && !note.isBlank()) notes.put(call.id(), note);
                }
                case PrecheckResult.Blocked(String reason) -> {
                    blocked.add(ToolExecutionResultMessage.builder().id(call.id()).toolName(call.name())
                            .text(BLOCKED_PREFIX + reason).isError(true).build());
                    trace.record(context.runId(), "precheck blocked", call.name() + " " + call.arguments(), reason);
                }
            }
        }
        return new PrecheckOutcome(allowed, blocked, new LinkedHashMap<>(notes));
    }

    private PrecheckResult run(RunContext context, ToolExecutionRequest call) {
        SensitivePrecheck precheck = prechecks.get(call.name());
        if (precheck == null) return PrecheckResult.allowed(null);
        try {
            return scope.runAs(context.caller(), () -> precheck.check(PrecheckArgs.of(call.arguments())));
        } catch (RuntimeException e) {
            return PrecheckResult.blocked(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }
}
