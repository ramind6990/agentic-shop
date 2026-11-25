package com.rmrx.agenticshop.app.identity.entity;

/**
 * Platform roles. Customers and staff share the app_user table; the role decides what a user may see
 * (see {@code security.RolePermissions} for endpoints and {@code security.AccessScope} for rows).
 */
public enum Role {
    CUSTOMER,
    SELLER,
    SUPPORT_T1,
    SUPPORT_T2,
    FINANCE,
    FRAUD,
    ADMIN;

    public boolean isStaff() {
        return this != CUSTOMER && this != SELLER;
    }

    /**
     * Whether this role meets a {@code min_role} on a document or section. Roles are not a ladder (finance does not
     * see fraud data), so each minimum lists who meets it; ADMIN meets every one.
     */
    public boolean satisfies(Role min) {
        return switch (min) {
            case CUSTOMER -> true;
            case SELLER -> this == SELLER || isStaff();
            case SUPPORT_T1 -> this == SUPPORT_T1 || this == SUPPORT_T2 || this == ADMIN;
            case SUPPORT_T2, FINANCE, FRAUD, ADMIN -> this == min || this == ADMIN;
        };
    }
}
