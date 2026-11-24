package com.rmrx.agenticshop.security;

import com.rmrx.agenticshop.identity.entity.Role;

import java.util.Set;

/** The signed-in user, as carried by the access token. sellerId is only set for role SELLER. */
public record CurrentUser(Long id, String username, Role role, Long sellerId) {

    public Set<Permission> permissions() {
        return RolePermissions.of(role);
    }

    public boolean has(Permission permission) {
        return permissions().contains(permission);
    }
}
