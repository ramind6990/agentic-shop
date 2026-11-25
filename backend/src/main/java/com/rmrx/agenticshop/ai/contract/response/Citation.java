package com.rmrx.agenticshop.ai.contract.response;

import java.time.LocalDate;

/**
 * A policy section an answer relies on, pinned to the document version that was retrieved: the version's validity
 * (effectiveTo exclusive, null = still in force), the chunk id and a short snippet of the chunk text. Built by code
 * from the retrieved chunks, never from model text. effectiveTo and chunkId were added within schemaVersion 1
 * (null in older envelopes).
 */
public record Citation(Long documentId, String documentTitle, String documentType, Long versionId,
                       String versionLabel, Long sectionId, String sectionHeading, LocalDate effectiveFrom,
                       LocalDate effectiveTo, Long chunkId, String snippet) {
}
