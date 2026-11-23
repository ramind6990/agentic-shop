package com.rmrx.agenticshop.identity.entity;

/**
 * Platform roles. Customers and staff share the app_user table; the role decides what a user may see
 * (enforced later, once authentication and permission checks are added).
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
}
