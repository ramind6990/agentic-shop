package com.rmrx.agenticshop.security;

/**
 * What a user may call. Each endpoint requires one permission ({@code @PreAuthorize("hasAuthority('...')")});
 * which rows it returns is decided afterwards by {@link AccessScope}.
 */
public enum Permission {
    CATALOG_READ,
    ORDERS_READ,
    RETURNS_READ,
    REFUNDS_READ,
    CUSTOMERS_READ,
    SELLERS_READ,
    /** Seller contracts and payouts. */
    SELLER_FINANCE_READ,
    /** Staff and seller accounts. */
    USERS_READ,
    TICKETS_READ,
    /** Staff-only internal notes in ticket threads. */
    TICKET_INTERNAL_READ,
    FLAGS_READ,
    DOCUMENTS_READ,
    /** The caller's own AI token usage and cost, and the usage of tickets they can see. */
    AI_USAGE_READ_OWN,
    /** Any user's AI token usage and cost. */
    AI_USAGE_READ_ALL,
    /** Agent run traces (model and tool steps). */
    AI_TRACE_READ
}
