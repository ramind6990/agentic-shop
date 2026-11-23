package com.rmrx.agenticshop.docs.service;

import com.rmrx.agenticshop.common.dto.RefMapper;
import com.rmrx.agenticshop.docs.dto.DocumentDtos.*;
import com.rmrx.agenticshop.docs.entity.Document;
import com.rmrx.agenticshop.docs.entity.DocumentSection;
import com.rmrx.agenticshop.docs.entity.DocumentVersion;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(uses = RefMapper.class)
public interface DocumentMapper {

    DocumentSummary toSummary(Document document);

    DocumentDetail toDetail(Document document, List<VersionSummary> versions);

    VersionSummary toVersionSummary(DocumentVersion version);

    SectionDto toSection(DocumentSection section);

    @Mapping(target = "code", source = "document.code")
    @Mapping(target = "title", source = "document.title")
    @Mapping(target = "type", source = "document.type")
    VersionDetail toVersionDetail(DocumentVersion version);
}
