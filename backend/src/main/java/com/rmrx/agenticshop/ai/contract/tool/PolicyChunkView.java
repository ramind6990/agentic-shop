package com.rmrx.agenticshop.ai.contract.tool;

import java.time.LocalDate;

/**
 * A retrieved policy chunk with what is needed to cite it. Only chunks the caller may see are returned; score is
 * the retrieval similarity (higher is closer).
 */
public record PolicyChunkView(String text, Long documentId, String documentTitle, String documentType,
                              Long versionId, String versionLabel, Long sectionId, String sectionHeading,
                              LocalDate effectiveFrom, double score) {
}
