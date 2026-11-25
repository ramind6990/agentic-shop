package com.rmrx.agenticshop.ai.rag;

import com.rmrx.agenticshop.ai.config.AiProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Heading-aware, deterministic chunking of one document version.
 *
 * <p>Each section is chunked on its own: a chunk never crosses a section boundary, because sections are the ACL unit
 * (each has its own min_role). A section up to {@code targetChars} (~600 tokens at 4 chars/token) is one chunk.
 * A longer one is split into paragraphs, paragraphs longer than the target into sentences, and sentences longer than
 * the target at word boundaries; the pieces are packed greedily up to the target, and each chunk after the first
 * starts with the last sentences of the previous one (up to {@code overlapChars}). A trailing chunk shorter than
 * {@code minChars} is merged into the previous one when the result stays within {@code maxChars} (~800 tokens).
 *
 * <p>The embedded text of a chunk is its body prefixed with the document title, version and validity, and the
 * section heading, so a chunk is self-describing in the vector space; the stored {@code text} is the body alone.
 * A version without sections is chunked from its summary (section null).
 */
@Component
public class PolicyChunker {

    private static final Pattern PARAGRAPH = Pattern.compile("\\n\\s*\\n");
    private static final Pattern SENTENCE = Pattern.compile("(?<=[.!?])\\s+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final int targetChars;
    private final int maxChars;
    private final int minChars;
    private final int overlapChars;

    public PolicyChunker(AiProperties props) {
        AiProperties.Rag rag = props.rag();
        this.targetChars = rag.chunkTargetChars();
        this.maxChars = Math.max(rag.chunkMaxChars(), rag.chunkTargetChars());
        this.minChars = Math.min(rag.chunkMinChars(), rag.chunkTargetChars());
        this.overlapChars = Math.min(rag.chunkOverlapChars(), rag.chunkTargetChars() / 4);
    }

    /** One version with its document metadata (the ACL columns) and its sections in ordinal order. */
    public record VersionSource(long documentId, String docCode, String docType, String docTitle, String docMinRole,
                                Long sellerId, long versionId, int versionNo, java.time.LocalDate effectiveFrom,
                                java.time.LocalDate effectiveTo, String summary, List<SectionSource> sections) {

        public String versionLabel() {
            return "v" + versionNo;
        }
    }

    public record SectionSource(long sectionId, int ordinal, String heading, String body, String minRole) {
    }

    /** A chunk of one section (or of the summary when sectionId is null). */
    public record Chunk(Long sectionId, Integer sectionOrdinal, String sectionHeading, String sectionMinRole,
                        int chunkIndex, String text, String embeddedText, String headingPath) {
    }

    public List<Chunk> chunk(VersionSource version) {
        List<Chunk> chunks = new ArrayList<>();
        if (version.sections().isEmpty()) {
            String summary = version.summary() == null ? "" : version.summary().strip();
            if (summary.isEmpty()) return chunks;
            List<String> bodies = split(summary);
            for (int i = 0; i < bodies.size(); i++) {
                chunks.add(new Chunk(null, null, null, version.docMinRole(), i, bodies.get(i),
                        embedded(version, "Summary", bodies.get(i)), version.docTitle()));
            }
            return chunks;
        }
        for (SectionSource section : version.sections()) {
            String body = section.body() == null ? "" : section.body().strip();
            if (body.isEmpty()) continue;
            List<String> bodies = split(body);
            String path = version.docTitle() + " > " + section.heading();
            for (int i = 0; i < bodies.size(); i++) {
                chunks.add(new Chunk(section.sectionId(), section.ordinal(), section.heading(), section.minRole(), i,
                        bodies.get(i), embedded(version, section.heading(), bodies.get(i)), path));
            }
        }
        return chunks;
    }

    private static String embedded(VersionSource v, String heading, String body) {
        String validity = "effective " + v.effectiveFrom() + (v.effectiveTo() == null ? ", current" : " to " + v.effectiveTo());
        return v.docTitle() + " (" + v.versionLabel() + ", " + validity + ")\n"
                + "Section: " + heading + "\n\n" + body;
    }

    /** The bodies of the chunks of one section text. */
    List<String> split(String text) {
        if (text.length() <= targetChars) return List.of(text);

        // pieces of at most the target size; newParagraph says how a piece joins the previous one
        List<Unit> units = new ArrayList<>();
        for (String paragraph : PARAGRAPH.split(text)) {
            String p = paragraph.strip();
            if (p.isEmpty()) continue;
            if (p.length() <= targetChars) {
                units.add(new Unit(p, true));
                continue;
            }
            boolean first = true;
            for (String sentence : SENTENCE.split(p)) {
                List<String> pieces = sentence.length() <= targetChars ? List.of(sentence) : splitWords(sentence);
                for (String piece : pieces) {
                    units.add(new Unit(piece, first));
                    first = false;
                }
            }
        }

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int fresh = 0; // chars in current beyond the overlap carried from the previous chunk
        for (Unit unit : units) {
            String sep = current.isEmpty() ? "" : unit.newParagraph() ? "\n\n" : " ";
            if (fresh > 0 && current.length() + sep.length() + unit.text().length() > targetChars) {
                String done = current.toString();
                chunks.add(done);
                current.setLength(0);
                String overlap = tailSentences(done);
                if (!overlap.isEmpty() && overlap.length() + 1 + unit.text().length() <= targetChars) {
                    current.append(overlap);
                    sep = unit.newParagraph() ? "\n\n" : " ";
                } else {
                    sep = "";
                }
                fresh = 0;
            }
            current.append(sep).append(unit.text());
            fresh += sep.length() + unit.text().length();
        }
        if (fresh > 0) {
            String last = current.toString();
            String freshText = last.substring(last.length() - fresh).strip();
            if (!chunks.isEmpty() && freshText.length() < minChars
                    && chunks.getLast().length() + 2 + freshText.length() <= maxChars) {
                chunks.set(chunks.size() - 1, chunks.getLast() + "\n\n" + freshText);
            } else {
                chunks.add(last.strip());
            }
        }
        return chunks;
    }

    private record Unit(String text, boolean newParagraph) {
    }

    /** The last sentences of a chunk, up to the overlap size (empty when even the last sentence is longer). */
    private String tailSentences(String chunk) {
        if (overlapChars <= 0) return "";
        String[] sentences = SENTENCE.split(chunk);
        StringBuilder tail = new StringBuilder();
        for (int i = sentences.length - 1; i >= 0; i--) {
            String s = sentences[i].strip();
            if (tail.length() + s.length() + 1 > overlapChars) break;
            tail.insert(0, tail.isEmpty() ? s : s + " ");
        }
        return tail.toString();
    }

    /** Splits an over-long sentence at word boundaries into pieces of at most the target size. */
    private List<String> splitWords(String sentence) {
        List<String> pieces = new ArrayList<>();
        StringBuilder piece = new StringBuilder();
        for (String word : WHITESPACE.split(sentence)) {
            if (word.isEmpty()) continue;
            if (!piece.isEmpty() && piece.length() + 1 + word.length() > targetChars) {
                pieces.add(piece.toString());
                piece.setLength(0);
            }
            if (word.length() > targetChars) { // no whitespace at all: hard cut
                for (int i = 0; i < word.length(); i += targetChars) {
                    pieces.add(word.substring(i, Math.min(word.length(), i + targetChars)));
                }
                continue;
            }
            if (!piece.isEmpty()) piece.append(' ');
            piece.append(word);
        }
        if (!piece.isEmpty()) pieces.add(piece.toString());
        return pieces;
    }
}
