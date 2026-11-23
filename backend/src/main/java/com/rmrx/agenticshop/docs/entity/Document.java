package com.rmrx.agenticshop.docs.entity;

import com.rmrx.agenticshop.common.entity.BaseEntity;
import com.rmrx.agenticshop.identity.entity.Role;
import com.rmrx.agenticshop.seller.entity.Seller;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A document in one of the corpora (policies, SOPs, seller contracts, fraud playbook).
 * minRole and seller carry the ACL metadata later used by permission-aware retrieval.
 */
@Getter
@Entity
@Table(name = "document")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Document extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentType type;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "min_role", nullable = false)
    private Role minRole;

    /** Set for tenant-scoped documents (seller contracts). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id")
    private Seller seller;
}
