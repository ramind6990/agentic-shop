package com.rmrx.agenticshop.ai2.graph.guard.injection;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Deterministic prompt-injection detection: phrases that try to override the assistant's rules, extract its prompt,
 * change its role, fake chat-format markers, or (in text written by third parties) address an AI assistant directly.
 * Cheap and predictable; whatever it calls SUSPECT can be confirmed by {@link InjectionClassifier}.
 */
@Component
public class PatternInjectionDetector {

    private record Rule(String name, Pattern pattern, InjectionVerdict.Level level) {
    }

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.MULTILINE;

    /** Rules for every text. */
    private static final List<Rule> COMMON = List.of(
            rule("override instructions", InjectionVerdict.Level.ATTACK,
                    "\\b(ignore|disregard|forget|override|bypass)\\b[^.\\n]{0,40}\\b(previous|prior|above|earlier|"
                            + "all|any|your|system)\\b[^.\\n]{0,30}\\b(instructions?|rules|prompts?|guidelines|"
                            + "directives|restrictions)\\b"),
            rule("reveal prompt", InjectionVerdict.Level.ATTACK,
                    "\\b(reveal|show|print|repeat|output|display|leak|dump|tell me|give me|what (is|are))\\b[^.\\n]{0,40}"
                            + "\\b(system prompt|system message|your (instructions|rules|prompt|guidelines)|hidden "
                            + "instructions|initial prompt|developer (message|instructions))\\b"),
            rule("role change", InjectionVerdict.Level.ATTACK,
                    "\\byou are now\\b|\\bfrom now on,? you are\\b|\\bact as (an? )?(unrestricted|"
                            + "unfiltered|jailbroken|evil|dan)\\b|\\bdeveloper mode\\b|\\bjailbreak|\\bdo anything now\\b"),
            rule("chat markers", InjectionVerdict.Level.ATTACK,
                    "<\\|?(im_start|im_end|system|endoftext|assistant)\\|?>|\\[/?INST]|<<SYS>>|</?(system|assistant|"
                            + "developer|tool_call|function_call)>|^\\s*#{1,3}\\s*(system|developer)\\b"),
            rule("new instructions", InjectionVerdict.Level.ATTACK,
                    "\\b(new|updated|real|actual|admin|system) (instructions?|rules|orders)\\s*:|\\bsystem override\\b"
                            + "|^\\s*(system|developer)\\s*:"),
            rule("tool forcing", InjectionVerdict.Level.SUSPECT,
                    "\\b(call|use|invoke|run|execute|trigger)\\b[^.\\n]{0,25}\\b(tool|function|issue_refund|cancel_order|"
                            + "escalate_to_human|approve_refund_request|update_shipping_address)\\b"),
            rule("encoded payload", InjectionVerdict.Level.SUSPECT, "[A-Za-z0-9+/]{200,}={0,2}"));

    /** Extra rules for text written by third parties: nobody writing a review needs to address an AI. */
    private static final List<Rule> THIRD_PARTY = List.of(
            rule("addresses an AI", InjectionVerdict.Level.ATTACK,
                    "\\b(AI|A\\.I\\.|chat ?bot|language model|LLM|GPT|virtual assistant)s?\\b[^.\\n]{0,40}\\b(must|"
                            + "should|need to|have to|are (instructed|required|told) to|shall|will now)\\b"),
            rule("note to the AI", InjectionVerdict.Level.ATTACK,
                    "\\b(note|message|instructions?|attention) (to|for) (the |any )?(AI|assistant|chat ?bot|agent|"
                            + "model|LLM)\\b"),
            rule("action request", InjectionVerdict.Level.SUSPECT,
                    "\\b(refund|cancel|approve|escalate)\\b[^.\\n]{0,30}\\b(immediately|right now|without (asking|"
                            + "approval|confirmation))\\b"));

    public InjectionVerdict detect(String text, TextSource source) {
        String normalized = TextSanitizer.forDetection(text);
        if (normalized.isBlank()) return InjectionVerdict.none();
        List<String> attacks = new ArrayList<>();
        List<String> suspects = new ArrayList<>();
        apply(COMMON, normalized, attacks, suspects);
        if (source == TextSource.THIRD_PARTY) apply(THIRD_PARTY, normalized, attacks, suspects);
        if (!attacks.isEmpty()) return new InjectionVerdict(InjectionVerdict.Level.ATTACK, attacks);
        if (!suspects.isEmpty()) return new InjectionVerdict(InjectionVerdict.Level.SUSPECT, suspects);
        return InjectionVerdict.none();
    }

    private static void apply(List<Rule> rules, String text, List<String> attacks, List<String> suspects) {
        for (Rule r : rules) {
            if (!r.pattern().matcher(text).find()) continue;
            (r.level() == InjectionVerdict.Level.ATTACK ? attacks : suspects).add(r.name());
        }
    }

    private static Rule rule(String name, InjectionVerdict.Level level, String regex) {
        return new Rule(name, Pattern.compile(regex, FLAGS), level);
    }
}
