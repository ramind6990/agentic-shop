package com.rmrx.agenticshop.security;

import com.rmrx.agenticshop.identity.entity.Role;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.rmrx.agenticshop.security.Permission.*;

/**
 * The fixed permission set of each role. Permissions are not stored in tokens, so a change here applies to the next
 * request of every signed-in user.
 */
public final class RolePermissions {

    private static final Map<Role, Set<Permission>> PERMISSIONS = new EnumMap<>(Role.class);

    static {
        PERMISSIONS.put(Role.CUSTOMER, EnumSet.of(CATALOG_READ, ORDERS_READ, RETURNS_READ, REFUNDS_READ,
                TICKETS_READ, DOCUMENTS_READ, AI_USAGE_READ_OWN, AI_CHAT));
        PERMISSIONS.put(Role.SELLER, EnumSet.of(CATALOG_READ, ORDERS_READ, RETURNS_READ, REFUNDS_READ,
                TICKETS_READ, SELLERS_READ, SELLER_FINANCE_READ, DOCUMENTS_READ, AI_USAGE_READ_OWN));
        // support assigns tickets, so it lists staff (USERS_READ)
        Set<Permission> support = EnumSet.of(CATALOG_READ, ORDERS_READ, RETURNS_READ, REFUNDS_READ,
                CUSTOMERS_READ, SELLERS_READ, USERS_READ, TICKETS_READ, TICKET_INTERNAL_READ, DOCUMENTS_READ,
                AI_USAGE_READ_OWN, AI_TRACE_READ, AI_CHAT);
        PERMISSIONS.put(Role.SUPPORT_T1, support);
        PERMISSIONS.put(Role.SUPPORT_T2, EnumSet.copyOf(support));
        PERMISSIONS.put(Role.FINANCE, EnumSet.of(CATALOG_READ, ORDERS_READ, RETURNS_READ, REFUNDS_READ,
                CUSTOMERS_READ, SELLERS_READ, SELLER_FINANCE_READ, DOCUMENTS_READ, AI_USAGE_READ_OWN,
                AI_USAGE_READ_ALL));
        PERMISSIONS.put(Role.FRAUD, EnumSet.of(CATALOG_READ, ORDERS_READ, RETURNS_READ, REFUNDS_READ,
                CUSTOMERS_READ, SELLERS_READ, TICKETS_READ, TICKET_INTERNAL_READ, FLAGS_READ, DOCUMENTS_READ,
                AI_USAGE_READ_OWN));
        // ADMIN has every permission, incl. AI_RAG_ADMIN (policy reindex)
        PERMISSIONS.put(Role.ADMIN, EnumSet.allOf(Permission.class));
    }

    private RolePermissions() {
    }

    public static Set<Permission> of(Role role) {
        return PERMISSIONS.get(role);
    }
}
