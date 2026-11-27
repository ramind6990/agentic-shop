package com.rmrx.agenticshop.ai.graph.guard;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * The ai guardrails ({@code app.ai.guard}).
 *
 * @param modelName model of the guard's own calls (classifier, auditor); null = the agent's model
 */
@ConfigurationProperties("app.ai.guard")
public record AiGuardProperties(@DefaultValue Input input, @DefaultValue ToolOutput toolOutput,
                                 @DefaultValue Output output, @DefaultValue Loop loop, String modelName) {

    /**
     * The user's own text: the message that starts a run and free-text answers to {@code ask_input}.
     *
     * @param mode       what a detected injection does (ENFORCE: the message is refused, the answer withheld)
     * @param classifier also ask the guard model when the patterns find nothing certain
     */
    public record Input(@DefaultValue("ENFORCE") GuardMode mode, @DefaultValue("true") boolean classifier) {
    }

    /**
     * Free text written by other people inside tool results ({@code @FreeText} fields: reviews, ticket messages, ...).
     *
     * @param injection  what a detected injection does (ENFORCE: the text is hidden from the model)
     * @param classifier ask the guard model about texts the patterns find suspicious (cached per text)
     * @param redactPii  replace emails, phone numbers, card numbers and IBANs in those texts
     * @param cacheSize  classifier verdicts kept in memory
     */
    public record ToolOutput(@DefaultValue("ENFORCE") GuardMode injection, @DefaultValue("true") boolean classifier,
                             @DefaultValue("true") boolean redactPii, @DefaultValue("1000") int cacheSize) {
    }

    /**
     * The final reply.
     *
     * @param grounding        order, ticket, approval and tracking numbers in the text must appear in the evidence
     * @param amounts          money amounts in the text must appear in the evidence (sums give false positives)
     * @param links            links only to {@code allowedLinkHosts}
     * @param auditor          the reply auditor (claimed actions, promises, contradictions)
     * @param allowedLinkHosts hosts a reply may link to; empty = no links at all
     * @param evidenceChars    evidence sent to the auditor, newest kept when it is longer
     */
    public record Output(@DefaultValue("ENFORCE") GuardMode grounding, @DefaultValue("SHADOW") GuardMode amounts,
                         @DefaultValue("ENFORCE") GuardMode links, @DefaultValue("ENFORCE") GuardMode auditor,
                         List<String> allowedLinkHosts, @DefaultValue("12000") int evidenceChars) {

        public Output {
            allowedLinkHosts = allowedLinkHosts == null ? List.of() : List.copyOf(allowedLinkHosts);
        }
    }

    /**
     * Loop detection inside one run.
     *
     * @param maxFailuresPerTool  failures of one tool after which further calls to it are blocked
     * @param maxToolErrors       failed tool calls after which the run is handed over (customer) or stopped (staff)
     * @param maxCallsPerReadTool distinct calls of one read tool after which further ones are blocked
     * @param maxIdleSteps        consecutive steps without any successful result after which the run is handed over
     */
    public record Loop(@DefaultValue("true") boolean enabled, @DefaultValue("2") int maxFailuresPerTool,
                       @DefaultValue("3") int maxToolErrors, @DefaultValue("5") int maxCallsPerReadTool,
                       @DefaultValue("2") int maxIdleSteps) {
    }
}
