package com.rmrx.agenticshop.docs.service;

import com.rmrx.agenticshop.common.dto.PageResponse;
import com.rmrx.agenticshop.common.exception.NotFoundException;
import com.rmrx.agenticshop.common.repository.Specs;
import com.rmrx.agenticshop.docs.dto.DocumentDtos.*;
import com.rmrx.agenticshop.docs.entity.Document;
import com.rmrx.agenticshop.docs.entity.DocumentType;
import com.rmrx.agenticshop.docs.repository.DocumentRepository;
import com.rmrx.agenticshop.docs.repository.DocumentVersionRepository;
import com.rmrx.agenticshop.security.AccessScope;
import com.rmrx.agenticshop.security.CurrentUser;
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
    private final AccessScope scope;

    public DocumentService(DocumentRepository documentRepository, DocumentVersionRepository versionRepository,
                           DocumentMapper mapper, AccessScope scope) {
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
        this.mapper = mapper;
        this.scope = scope;
    }

    public PageResponse<DocumentSummary> search(DocumentType type, Long sellerId, String q, Pageable pageable) {
        Specification<Document> spec = Specification.allOf(
                Specs.eq("type", type),
                Specs.eq("seller.id", sellerId),
                Specs.containsIgnoreCase(q, "title", "code"),
                visibleToCaller());
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
                .map(v -> visibleSections(doc, mapper.toVersionDetail(v)))
                .orElseThrow(() -> new NotFoundException("Document version", code + " v" + versionNo));
    }

    /** The version that was in force on the given date (e.g. the return policy that applies to an order). */
    public VersionDetail effective(String code, LocalDate at) {
        Document doc = find(code);
        LocalDate date = at == null ? LocalDate.now() : at;
        return versionRepository.findEffective(doc.getId(), date)
                .map(v -> visibleSections(doc, mapper.toVersionDetail(v)))
                .orElseThrow(() -> new NotFoundException("Document version", code + " in force at " + date));
    }

    private Document find(String code) {
        return documentRepository.findByCode(code).filter(scope::canSeeDocument)
                .orElseThrow(() -> new NotFoundException("Document", code));
    }

    /** Drops the sections the caller's role does not meet. */
    private VersionDetail visibleSections(Document doc, VersionDetail v) {
        return new VersionDetail(v.code(), v.title(), v.type(), v.versionNo(), v.effectiveFrom(), v.effectiveTo(),
                v.summary(), v.sections().stream().filter(sec -> scope.canSeeSection(doc, sec.minRole())).toList());
    }

    /** Same rule as {@link AccessScope#canSeeDocument}, as a query filter for the document list. */
    private Specification<Document> visibleToCaller() {
        CurrentUser user = scope.current();
        Specification<Document> byRole = Specs.in("minRole", scope.visibleMinRoles());
        Specification<Document> tenantRule = user.role().isStaff() ? Specs.all()
                : (root, query, cb) -> cb.isNull(root.get("seller"));
        Specification<Document> ownTenantDoc = user.sellerId() == null ? null
                : Specs.eq("seller.id", user.sellerId());
        Specification<Document> general = byRole.and(tenantRule);
        return ownTenantDoc == null ? general : general.or(ownTenantDoc);
    }
}
