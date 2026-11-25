package com.rmrx.agenticshop.ai.web;

import com.rmrx.agenticshop.ai.conversation.ConversationExceptions;
import com.rmrx.agenticshop.ai.rag.PolicyIngestionService;
import com.rmrx.agenticshop.ai.rag.RagDtos.RagStats;
import com.rmrx.agenticshop.ai.rag.RagDtos.ReindexResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Policy RAG corpus administration (ADMIN). Reindexing is synchronous and idempotent: unchanged versions cost no
 * embedding call; a version whose embedding fails keeps its previous chunks and is listed in {@code errors} (still
 * 200). 409 while another reindex runs, 503 when AI is disabled.
 */
@RestController
@RequestMapping("/api/ai/admin/rag")
@PreAuthorize("hasAuthority('AI_RAG_ADMIN')")
public class RagAdminController {

    private final PolicyIngestionService ingestion;

    public RagAdminController(PolicyIngestionService ingestion) {
        this.ingestion = ingestion;
    }

    @PostMapping("/reindex")
    public ReindexResult reindexAll() {
        return ingestion.reindexAll();
    }

    @PostMapping("/reindex/versions/{versionId}")
    public ReindexResult reindexVersion(@PathVariable long versionId) {
        return ingestion.reindexVersion(versionId);
    }

    /** Chunk counts per doc type, min_role, model and document version (optionally one doc type). */
    @GetMapping("/stats")
    public RagStats stats(@RequestParam(required = false) String docType) {
        return ingestion.stats(docType);
    }

    @ExceptionHandler(ConversationExceptions.AiUnavailable.class)
    ProblemDetail unavailable(ConversationExceptions.AiUnavailable ex) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "AI unavailable", ex.getMessage());
    }

    @ExceptionHandler(PolicyIngestionService.ReindexInProgress.class)
    ProblemDetail inProgress(PolicyIngestionService.ReindexInProgress ex) {
        return problem(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        return pd;
    }
}
