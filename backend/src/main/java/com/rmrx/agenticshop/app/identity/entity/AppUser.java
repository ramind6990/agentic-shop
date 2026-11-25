package com.rmrx.agenticshop.app.identity.entity;

import com.rmrx.agenticshop.app.common.entity.BaseEntity;
import com.rmrx.agenticshop.app.seller.entity.Seller;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Customer or staff user. The role decides what the user may see; sellers' users point to their seller. */
@Getter
@Entity
@Table(name = "app_user")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUser extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String email;

    /** Login name; null for users who cannot sign in. */
    @Column(unique = true)
    private String username;

    /** BCrypt hash of the password; null for users who cannot sign in. */
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /** Only set for users with role SELLER: the seller (tenant) they belong to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id")
    private Seller seller;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
