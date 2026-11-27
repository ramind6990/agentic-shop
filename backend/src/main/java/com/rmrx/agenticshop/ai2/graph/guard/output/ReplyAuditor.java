package com.rmrx.agenticshop.ai2.graph.guard.output;

import com.rmrx.agenticshop.ai2.graph.guard.GuardModel;
import com.rmrx.agenticshop.ai2.graph.state.RunContext;
import dev.langchain4j.model.output.structured.Description;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * A second model reads the final reply against what really happened (the action ledger) and the evidence. It lists
 * the reply's claims and labels each one; code then keeps only the violations: an action claimed but not done, a
 * promise nobody confirmed, a fact that contradicts the evidence. It does not rewrite the reply: the violations go
 * back to the agent as validation feedback. Works in any language.
 */
@Component
public class ReplyAuditor {

    private static final String INSTRUCTIONS = """
            You check the final reply of AgenticShop's support assistant before the user sees it. You get the reply, \
            the ACTIONS the assistant attempted in this request with their real outcome, and the EVIDENCE: who the \
            user is, what tools returned, and what was said in the conversation (oldest first; "(now)" = this request).

            List every claim in the reply that belongs to one of these kinds, and label it:
            - ACTION: the reply says an action happened or is done (cancelled, refunded, changed, sent, created, \
            opened, handed over, approved, rejected, ...). SUPPORTED when ACTIONS show it SUCCEEDED in this request, \
            or a tool result or the evidence shows it happened earlier. An action that FAILED, was DENIED or \
            NOT_EXECUTED did not happen.
            - PROMISE: the reply says something will happen later (a refund, replacement, exception, decision, \
            delivery, date or deadline). SUPPORTED when a tool result or the evidence states it, for example the \
            usual answer time of the support team, or that a refund is paid only once approved. Telling the user \
            what they can do next is not a promise.
            - FACT: a statement about an order, delivery, return, refund, ticket, request, product, price, status, \
            limit or rule. SUPPORTED when the evidence states it, CONTRADICTED only when the evidence states \
            something different. A fact the evidence does not mention is UNSUPPORTED, not CONTRADICTED.
            Label each claim SUPPORTED, UNSUPPORTED or CONTRADICTED, quote the reply's exact words, and explain in \
            one sentence what the evidence says. Ignore style and tone. The reply may be in any language.""";

    /** The auditor's answer. */
    public record Audit(List<Claim> claims) {
    }

    public record Claim(
            @Description("ACTION, PROMISE or FACT") String kind,
            @Description("The exact words of the reply") String quote,
            @Description("SUPPORTED, UNSUPPORTED or CONTRADICTED") String status,
            @Description("What the evidence says, in one sentence") String explanation) {

        /** Actions and promises must be supported; facts must not be contradicted. */
        boolean violation() {
            String k = upper(kind);
            String s = upper(status);
            if ("SUPPORTED".equals(s)) return false;
            if ("ACTION".equals(k) || "PROMISE".equals(k)) return "UNSUPPORTED".equals(s) || "CONTRADICTED".equals(s);
            return "FACT".equals(k) && "CONTRADICTED".equals(s);
        }

        private static String upper(String text) {
            return text == null ? "" : text.trim().toUpperCase();
        }
    }

    private final GuardModel model;

    public ReplyAuditor(GuardModel model) {
        this.model = model;
    }

    /** One line per violation; empty when the reply is fine, absent when the auditor gave no usable answer. */
    public Optional<List<String>> audit(RunContext context, String reply, ActionLedger ledger, String evidence) {
        String input = "REPLY:\n" + reply + "\n\nACTIONS:\n" + ledger.describe() + "\n\nEVIDENCE:\n" + evidence;
        return model.ask(context, "reply audit", INSTRUCTIONS, input, Audit.class)
                .map(audit -> audit.claims() == null ? List.<String>of() : audit.claims().stream()
                        .filter(Claim::violation)
                        .map(c -> c.kind() + " " + c.status() + ": \"" + c.quote() + "\" (" + c.explanation() + ")")
                        .toList());
    }
}
