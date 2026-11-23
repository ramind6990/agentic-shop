package com.rmrx.agenticshop.docs.service;

import com.rmrx.agenticshop.common.dto.PageResponse;
import com.rmrx.agenticshop.common.exception.NotFoundException;
import com.rmrx.agenticshop.common.repository.Specs;
import com.rmrx.agenticshop.docs.dto.DocumentDtos.*;
import com.rmrx.agenticshop.docs.entity.Document;
import com.rmrx.agenticshop.docs.entity.DocumentType;
import com.rmrx.agenticshop.docs.repository.DocumentRepository;
import com.rmrx.agenticshop.docs.repository.DocumentVersionRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@Transactional(readOnly = true)
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final DocumentMapper mapper;

    public DocumentService(DocumentRepository documentRepository, DocumentVersionRepository versionRepository,
                           DocumentMapper mapper) {
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
        this.mapper = mapper;
    }

    public PageResponse<DocumentSummary> search(DocumentType type, Long sellerId, String q, Pageable pageable) {
        Specification<Document> spec = Specification.allOf(
                Specs.eq("type", type),
                Specs.eq("seller.id", sellerId),
                Specs.containsIgnoreCase(q, "title", "code"));
        return PageResponse.of(documentRepository.findAll(spec, pageable), mapper::toSummary);
    }

    public DocumentDetail get(String code) {
        Document doc = find(code);
        return mapper.toDetail(doc, versionRepository.findByDocumentIdOrderByVersionNoAsc(doc.getId()).stream()
                .map(mapper::toVersionSummary).toList());
    }

    public VersionDetail version(String code, int versionNo) {
        Document doc = find(code);
        return versionRepository.findByDocumentIdAndVersionNo(doc.getId(), versionNo)
                .map(mapper::toVersionDetail)
                .orElseThrow(() -> new NotFoundException("Document version", code + " v" + versionNo));
    }

    /** The version that was in force on the given date (e.g. the return policy that applies to an order). */
    public VersionDetail effective(String code, LocalDate at) {
        Document doc = find(code);
        LocalDate date = at == null ? LocalDate.now() : at;
        return versionRepository.findEffective(doc.getId(), date)
                .map(mapper::toVersionDetail)
                .orElseThrow(() -> new NotFoundException("Document version", code + " in force at " + date));
    }

    private Document find(String code) {
        return documentRepository.findByCode(code).orElseThrow(() -> new NotFoundException("Document", code));
    }
}
