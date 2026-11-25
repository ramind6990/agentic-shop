package com.rmrx.agenticshop.ai.contract.tool;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDate;

/**
 * A retrieved policy chunk with what is needed to cite it. Only chunks the caller may see are returned. ref is the
 * 1-based position in the search result, which the model writes as {@code [ref]}; excerpt is the chunk text capped
 * for the prompt. The document/version/section ids and the fused score stay server-side (citations, traces) and are
 * not shown to the model.
 */
public record PolicyChunkView(int ref, Long chunkId, String documentTitle, String documentType,
                              String sectionHeading, String versionLabel, LocalDate effectiveFrom,
                              LocalDate effectiveTo, String excerpt,
                              @JsonIgnore Long documentId, @JsonIgnore Long versionId, @JsonIgnore Long sectionId,
                              @JsonIgnore double score) {
}
