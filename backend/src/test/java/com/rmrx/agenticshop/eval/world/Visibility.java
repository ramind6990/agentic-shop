package com.rmrx.agenticshop.eval.world;

import com.rmrx.agenticshop.app.identity.entity.Role;
import com.rmrx.agenticshop.app.security.AccessScope;
import com.rmrx.agenticshop.app.security.CurrentUser;

import java.util.Objects;

/**
 * Row visibility for the stubs, from the real {@link AccessScope} (tools run inside {@code runAs(caller)}): a customer
 * sees only their own rows, staff see all. Sellers are not modelled. The real tier tests the real scope; this only
 * keeps the fake world from showing a customer what the shop never would.
 */
public final class Visibility {

    private final AccessScope scope;

    public Visibility(AccessScope scope) {
        this.scope = scope;
    }

    public CurrentUser caller() {
        return scope.current();
    }

    public boolean visible(Long ownerId) {
        CurrentUser caller = caller();
        return caller.role() == Role.CUSTOMER ? Objects.equals(caller.id(), ownerId) : caller.role().isStaff();
    }

    /** The customer whose rows are asked for: the caller for a customer, else the requested one (may be null). */
    public Long customerFilter(Long requested) {
        CurrentUser caller = caller();
        return caller.role() == Role.CUSTOMER ? caller.id() : requested;
    }
}
