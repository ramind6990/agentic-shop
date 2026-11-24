package com.rmrx.agenticshop.ai.contract.response;

import java.time.LocalDate;

/** A policy section an answer relies on, pinned to the document version that was retrieved. */
public record Citation(Long documentId, String documentTitle, String documentType, Long versionId,
                       String versionLabel, Long sectionId, String sectionHeading, LocalDate effectiveFrom,
                       String snippet) {
}
