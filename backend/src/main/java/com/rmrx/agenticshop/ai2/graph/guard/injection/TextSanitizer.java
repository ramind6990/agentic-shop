package com.rmrx.agenticshop.ai2.graph.guard.injection;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Removes characters a person cannot see but a model reads: format characters (zero-width spaces, bidi overrides, the
 * Unicode "tag" block used to smuggle hidden ASCII) and control characters other than line breaks and tabs. The
 * zero-width joiner stays, so emoji sequences survive.
 */
public final class TextSanitizer {

    private static final Pattern INVISIBLE = Pattern.compile("[\\p{Cf}&&[^\\u200D]]|[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");

    private TextSanitizer() {
    }

    public static String clean(String text) {
        return text == null || text.isEmpty() ? text : INVISIBLE.matcher(text).replaceAll("");
    }

    /** The text the detectors read: cleaned and NFKC-normalized (full-width letters and look-alikes folded). */
    public static String forDetection(String text) {
        return text == null ? "" : Normalizer.normalize(clean(text), Normalizer.Form.NFKC);
    }
}
