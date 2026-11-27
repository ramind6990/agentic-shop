package com.rmrx.agenticshop.app.security;

import com.rmrx.agenticshop.app.common.exception.NotFoundException;
import com.rmrx.agenticshop.app.docs.entity.Document;
import com.rmrx.agenticshop.app.identity.entity.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Row-level access for the signed-in user, applied by services after the endpoint's permission check.
 * Customers only see their own data and seller users only their seller's; staff see all rows, and documents and
 * sections are filtered by {@code min_role}. Rows outside the scope look like they do not exist (404).
 */
@Component
public class AccessScope {

    /** The user work runs for outside a request thread (e.g. an agent's tool call), bound by {@link #runAs}. */
    private static final ScopedValue<CurrentUser> ACTING_USER = ScopedValue.newInstance();

    public CurrentUser current() {
        if (ACTING_USER.isBound()) return ACTING_USER.get();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) return TokenService.toCurrentUser(jwt.getToken());
        throw new IllegalStateException("No signed-in user");
    }

    /**
     * Runs {@code work} as {@code user}: every scope check inside sees that user, exactly as for their own request
     * (an agent acts with the permissions of the user it works for, never more).
     */
    public <T> T runAs(CurrentUser user, Supplier<T> work) {
        return ScopedValue.where(ACTING_USER, user).call(work::get);
    }

    /** Throws 404 unless the caller is staff (support, admin); others must not learn that the action exists. */
    public void requireStaff(String what) {
        if (!current().role().isStaff()) throw new NotFoundException(what, "staff only");
    }

    /** The customer filter to apply: the caller's own id for a CUSTOMER, otherwise the requested one. */
    public Long customerFilter(Long requested) {
        CurrentUser user = current();
        return user.role() == Role.CUSTOMER ? user.id() : requested;
    }

    /** The seller filter to apply: the caller's seller for a SELLER, otherwise the requested one. */
    public Long sellerFilter(Long requested) {
        CurrentUser user = current();
        return user.role() == Role.SELLER ? user.sellerId() : requested;
    }

    /** Throws 404 unless the row (owned by this customer and seller) is within the caller's scope. */
    public void requireOwned(Long customerId, Long sellerId, String what, Object id) {
        CurrentUser user = current();
        boolean visible = switch (user.role()) {
            case CUSTOMER -> Objects.equals(customerId, user.id());
            case SELLER -> Objects.equals(sellerId, user.sellerId());
            default -> true;
        };
        if (!visible) throw new NotFoundException(what, id);
    }

    /** The min_role values the caller meets. */
    public List<Role> visibleMinRoles() {
        Role role = current().role();
        return Arrays.stream(Role.values()).filter(role::satisfies).toList();
    }

    /**
     * A tenant document (seller contract) is visible to its own seller's users in full, and otherwise only to
     * roles meeting its min_role. Other sellers and customers never see tenant documents.
     */
    public boolean canSeeDocument(Document doc) {
        CurrentUser user = current();
        Long docSeller = doc.getSeller() == null ? null : doc.getSeller().getId();
        if (docSeller != null && docSeller.equals(user.sellerId())) return true;
        if (docSeller != null && !user.role().isStaff()) return false;
        return user.role().satisfies(doc.getMinRole());
    }

    /** Sections of a visible document: its own seller sees every section, others need the section's min_role. */
    public boolean canSeeSection(Document doc, Role sectionMinRole) {
        CurrentUser user = current();
        if (doc.getSeller() != null && doc.getSeller().getId().equals(user.sellerId())) return true;
        return user.role().satisfies(sectionMinRole);
    }
}
