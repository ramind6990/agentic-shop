package com.rmrx.agenticshop.ai.rag;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Results of the RAG admin endpoints. */
public final class RagDtos {

    private RagDtos() {
    }

    /**
     * Outcome of a reindex. A version is unchanged when its chunks, hashes and metadata all match (no writes);
     * chunksCreated were embedded now, embeddingsReused were moved/copied from a row of the same version with the same
     * content hash, chunksMetadataUpdated only had ACL/display columns refreshed. A failed version keeps its previous
     * chunks and is listed in errors; the other versions continue.
     */
    public record ReindexResult(int versionsTotal, int versionsChanged, int versionsUnchanged, int versionsFailed,
                                int chunksCreated, int chunksUnchanged, int chunksDeleted, int chunksMetadataUpdated,
                                int embeddingsReused, int embeddingCalls, long embeddingTokens, long durationMs,
                                List<VersionError> errors) {
    }

    public record VersionError(long versionId, String documentCode, String versionLabel, String error) {
    }

    public record CountRow(String key, long chunks) {
    }

    public record VersionCountRow(long versionId, String documentCode, String docType, String versionLabel,
                                  LocalDate effectiveFrom, LocalDate effectiveTo, long chunks, long sections,
                                  long tokens, Instant updatedAt) {
    }

    public record RagStats(long totalChunks, long versionsTotal, long versionsIndexed, List<CountRow> byDocType,
                           List<CountRow> bySectionMinRole, List<CountRow> byDocMinRole, List<CountRow> byModel,
                           List<VersionCountRow> byVersion) {
    }
}
