package com.rmrx.agenticshop.ai.graph.guard.injection;

import com.rmrx.agenticshop.ai.graph.state.RunContext;
import org.springframework.stereotype.Component;

/**
 * The two detectors combined: the patterns decide alone when they find an attack; otherwise the classifier (when
 * asked for) has the last word. Without a classifier answer, the patterns' verdict stands.
 */
@Component
public class InjectionDetection {

    private final PatternInjectionDetector patterns;
    private final InjectionClassifier classifier;

    public InjectionDetection(PatternInjectionDetector patterns, InjectionClassifier classifier) {
        this.patterns = patterns;
        this.classifier = classifier;
    }

    /**
     * @param classify NEVER, ON_SUSPECT (only when the patterns are unsure) or ALWAYS (unless they found an attack)
     */
    public InjectionVerdict detect(RunContext context, String text, TextSource source, Classify classify) {
        InjectionVerdict found = patterns.detect(text, source);
        if (found.attack() || !classify.wants(found)) return found;
        return classifier.classify(context, text, source).orElse(found);
    }

    public enum Classify {
        NEVER, ON_SUSPECT, ALWAYS;

        boolean wants(InjectionVerdict verdict) {
            return this == ALWAYS || this == ON_SUSPECT && verdict.level() == InjectionVerdict.Level.SUSPECT;
        }
    }
}
