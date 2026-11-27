package com.rmrx.agenticshop.ai2.graph.guard.output;

import com.rmrx.agenticshop.ai2.graph.agent.response.AgentResponse;
import com.rmrx.agenticshop.ai2.graph.agent.response.ResponseStatus;
import com.rmrx.agenticshop.ai2.graph.guard.Ai2GuardProperties;
import com.rmrx.agenticshop.ai2.graph.guard.GuardMode;
import com.rmrx.agenticshop.ai2.graph.guard.GuardTrace;
import com.rmrx.agenticshop.ai2.graph.state.RunState;
import com.rmrx.agenticshop.ai2.tools.ToolRegistry;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Guardrail "output": checks a final reply before the user sees it. Deterministic checks first (identifiers in the
 * text exist in the evidence, no links to other hosts, amounts in shadow); only a reply that passes them goes to the
 * {@link ReplyAuditor} (claimed actions, promises, contradictions). Each finding of an enforced check becomes
 * validation feedback for the agent; SHADOW findings only go to the trace. The auditor sends a reply back at most once
 * per validation round (its findings on the retried reply are traced only).
 */
@Component
public class OutputGuard {

    private final IdentifierGrounding identifiers;
    private final AmountGrounding amounts;
    private final LinkPolicy links;
    private final ReplyAuditor auditor;
    private final ReplyEvidence evidence;
    private final ToolRegistry registry;
    private final GuardTrace trace;
    private final Ai2GuardProperties.Output props;

    public OutputGuard(IdentifierGrounding identifiers, AmountGrounding amounts, LinkPolicy links,
                       ReplyAuditor auditor, ReplyEvidence evidence, ToolRegistry registry, GuardTrace trace,
                       Ai2GuardProperties props) {
        this.identifiers = identifiers;
        this.amounts = amounts;
        this.links = links;
        this.auditor = auditor;
        this.evidence = evidence;
        this.registry = registry;
        this.trace = trace;
        this.props = props.output();
    }

    /** The problems that reject the reply; empty when it may go to the user. */
    public List<String> check(RunState state, AgentResponse response) {
        String text = response.text();
        List<String> lines = evidence.collect(state);
        String all = String.join("\n", lines);
        List<String> errors = new ArrayList<>();
        errors.addAll(apply(state, "grounding", props.grounding(), () -> identifiers.check(text, all)));
        errors.addAll(apply(state, "links", props.links(), () -> links.check(text)));
        errors.addAll(apply(state, "amounts", props.amounts(), () -> amounts.check(text, all)));
        if (!errors.isEmpty() || response.status() != ResponseStatus.ANSWERED) return errors;
        // the auditor is a model and can be wrong: it may send a reply back once; on a retry it only traces, so a
        // false finding never turns a completed action into the fallback error reply
        GuardMode auditMode = state.validationRetries() > 0 && props.auditor().enforced()
                ? GuardMode.SHADOW : props.auditor();
        return apply(state, "reply audit", auditMode, () -> audit(state, text, lines));
    }

    private List<String> audit(RunState state, String text, List<String> lines) {
        ActionLedger ledger = ActionLedger.of(state, registry.catalog(state.context()));
        return auditor.audit(state.context(), text, ledger, newest(lines, props.evidenceChars())).orElseGet(() -> {
            trace.record(state.context().runId(), "reply audit unavailable", null, "the reply was not audited");
            return List.of();
        });
    }

    private List<String> apply(RunState state, String check, GuardMode mode, Supplier<List<String>> run) {
        if (!mode.runs()) return List.of();
        List<String> found = run.get();
        if (found.isEmpty()) return List.of();
        trace.record(state.context().runId(), check + (mode.enforced() ? " rejected the reply" : " (shadow)"),
                null, String.join("; ", found));
        return mode.enforced() ? found : List.of();
    }

    /** The newest lines that fit in {@code maxChars}, oldest first. */
    private static String newest(List<String> lines, int maxChars) {
        List<String> kept = new ArrayList<>();
        int size = 0;
        for (int i = lines.size() - 1; i >= 0; i--) {
            String line = lines.get(i);
            if (size + line.length() > maxChars && !kept.isEmpty()) break;
            kept.addFirst(line.length() > maxChars ? line.substring(0, maxChars) : line);
            size += line.length() + 1;
        }
        return String.join("\n", kept);
    }
}
