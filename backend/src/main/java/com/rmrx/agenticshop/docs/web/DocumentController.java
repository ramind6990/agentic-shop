package com.rmrx.agenticshop.docs.web;

import org.springframework.security.access.prepost.PreAuthorize;
import com.rmrx.agenticshop.common.dto.PageResponse;
import com.rmrx.agenticshop.common.web.Paging;
import com.rmrx.agenticshop.docs.dto.DocumentDtos.DocumentDetail;
import com.rmrx.agenticshop.docs.dto.DocumentDtos.DocumentSummary;
import com.rmrx.agenticshop.docs.dto.DocumentDtos.VersionDetail;
import com.rmrx.agenticshop.docs.entity.DocumentType;
import com.rmrx.agenticshop.docs.service.DocumentService;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/** Policy, SOP, contract and playbook documents with their versions, filtered by min_role and tenant. */
@RestController
@PreAuthorize("hasAuthority('DOCUMENTS_READ')")
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping
    public PageResponse<DocumentSummary> search(@RequestParam(required = false) DocumentType type,
                                                @RequestParam(required = false) Long sellerId,
                                                @RequestParam(required = false) String q,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return documentService.search(type, sellerId, q, Paging.of(page, size, Sort.by("type", "code")));
    }

    @GetMapping("/{code}")
    public DocumentDetail get(@PathVariable String code) {
        return documentService.get(code);
    }

    @GetMapping("/{code}/versions/{versionNo}")
    public VersionDetail version(@PathVariable String code, @PathVariable int versionNo) {
        return documentService.version(code, versionNo);
    }

    /** Version in force at a date (default: today), e.g. ?at=2025-03-01. */
    @GetMapping("/{code}/effective")
    public VersionDetail effective(@PathVariable String code,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                   LocalDate at) {
        return documentService.effective(code, at);
    }
}
