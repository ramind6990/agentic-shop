package com.rmrx.agenticshop.docs.dto;

import com.rmrx.agenticshop.common.dto.Ref;
import com.rmrx.agenticshop.docs.entity.DocumentType;
import com.rmrx.agenticshop.identity.entity.Role;

import java.time.LocalDate;
import java.util.List;

public final class DocumentDtos {

    private DocumentDtos() {
    }

    public record DocumentSummary(Long id, String code, DocumentType type, String title, Role minRole, Ref seller) {
    }

    public record VersionSummary(int versionNo, LocalDate effectiveFrom, LocalDate effectiveTo, String summary) {
    }

    public record DocumentDetail(Long id, String code, DocumentType type, String title, Role minRole, Ref seller,
                                 List<VersionSummary> versions) {
    }

    public record SectionDto(int ordinal, String heading, String body, Role minRole) {
    }

    public record VersionDetail(String code, String title, DocumentType type, int versionNo,
                                LocalDate effectiveFrom, LocalDate effectiveTo, String summary,
                                List<SectionDto> sections) {
    }
}
