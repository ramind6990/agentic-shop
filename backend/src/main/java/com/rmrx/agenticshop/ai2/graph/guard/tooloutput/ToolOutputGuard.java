package com.rmrx.agenticshop.ai2.graph.guard.tooloutput;

import com.rmrx.agenticshop.ai2.contract.interaction.InteractionJson;
import com.rmrx.agenticshop.ai2.contract.tool.ToolResponse;
import com.rmrx.agenticshop.ai2.graph.guard.Ai2GuardProperties;
import com.rmrx.agenticshop.ai2.graph.guard.GuardTrace;
import com.rmrx.agenticshop.ai2.graph.guard.injection.InjectionDetection;
import com.rmrx.agenticshop.ai2.graph.guard.injection.InjectionVerdict;
import com.rmrx.agenticshop.ai2.graph.guard.injection.TextSanitizer;
import com.rmrx.agenticshop.ai2.graph.guard.injection.TextSource;
import com.rmrx.agenticshop.ai2.graph.guard.pii.PiiRedactor;
import com.rmrx.agenticshop.ai2.graph.state.RunContext;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Guardrails on what the model reads from a tool: the agent summary of a result (already minimized by its type, e.g.
 * masked contact data) with each {@code @FreeText} string checked on the way:
 * <ul>
 * <li>invisible characters removed;</li>
 * <li>emails, phone numbers, card numbers and IBANs redacted (PII);</li>
 * <li>a text that looks like instructions aimed at an AI assistant (indirect prompt injection) replaced by
 *     {@link #HIDDEN}.</li>
 * </ul>
 * The displayed result is built from the full data and is not changed. Every hidden or flagged text is traced.
 */
@Component
public class ToolOutputGuard {

    public static final String HIDDEN = "[hidden by the shop: this text contained instructions aimed at an AI "
            + "assistant]";

    private final InjectionDetection detection;
    private final PiiRedactor pii;
    private final GuardTrace trace;
    private final Ai2GuardProperties.ToolOutput props;

    public ToolOutputGuard(InjectionDetection detection, PiiRedactor pii, GuardTrace trace,
                           Ai2GuardProperties props) {
        this.detection = detection;
        this.pii = pii;
        this.trace = trace;
        this.props = props.toolOutput();
    }

    /** The JSON the model gets for a successful result. */
    public String agentSummary(ToolResponse value, String toolName, RunContext context) {
        Object summary = value.toAgentSummary();
        JsonNode tree = InteractionJson.toTree(summary);
        List<String> flagged = new ArrayList<>();
        FreeTextWalker.walk(summary, tree, (path, text) -> screen(context, path, text, flagged));
        if (!flagged.isEmpty()) {
            trace.record(context.runId(), props.injection().enforced() ? "tool text hidden" : "tool text flagged "
                    + "(shadow)", toolName, String.join("; ", flagged));
        }
        return InteractionJson.write(tree);
    }

    private String screen(RunContext context, String path, String text, List<String> flagged) {
        String clean = TextSanitizer.clean(text);
        if (props.injection().runs()) {
            InjectionVerdict verdict = detection.detect(context, clean, TextSource.THIRD_PARTY, props.classifier()
                    ? InjectionDetection.Classify.ON_SUSPECT : InjectionDetection.Classify.NEVER);
            if (verdict.attack()) {
                flagged.add(path + ": " + verdict.describe());
                if (props.injection().enforced()) return HIDDEN;
            }
        }
        return props.redactPii() ? pii.redact(clean) : clean;
    }
}
