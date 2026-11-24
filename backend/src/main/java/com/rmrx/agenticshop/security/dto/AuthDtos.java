package com.rmrx.agenticshop.security.dto;

import com.rmrx.agenticshop.common.dto.Ref;
import com.rmrx.agenticshop.identity.entity.Role;
import com.rmrx.agenticshop.security.Permission;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.Set;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record LoginResponse(String token, Instant expiresAt) {
    }

    /** The session: who is signed in and what they may do. The frontend shows or hides UI by permissions. */
    public record Me(Long id, String username, String fullName, String email, Role role, Ref seller,
                     Set<Permission> permissions) {
    }
}
