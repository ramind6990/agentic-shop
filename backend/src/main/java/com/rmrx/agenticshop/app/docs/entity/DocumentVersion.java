package com.rmrx.agenticshop.app.docs.entity;

import com.rmrx.agenticshop.app.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** A version of a document, in force from effectiveFrom until effectiveTo (exclusive; null = current). */
@Getter
@Entity
@Table(name = "document_version")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentVersion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(columnDefinition = "text")
    private String summary;

    @OneToMany(mappedBy = "version")
    @OrderBy("ordinal")
    private List<DocumentSection> sections = new ArrayList<>();
}
