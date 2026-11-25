package com.rmrx.agenticshop.app.docs.entity;

import com.rmrx.agenticshop.app.common.entity.BaseEntity;
import com.rmrx.agenticshop.app.identity.entity.Role;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A section of a document version. minRole allows section-level ACLs (e.g. Tier-2-only SOP sections). */
@Getter
@Entity
@Table(name = "document_section")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentSection extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false)
    private DocumentVersion version;

    @Column(nullable = false)
    private int ordinal;

    @Column(nullable = false)
    private String heading;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "min_role", nullable = false)
    private Role minRole;
}
