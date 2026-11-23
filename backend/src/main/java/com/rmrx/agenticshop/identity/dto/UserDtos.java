package com.rmrx.agenticshop.identity.dto;

import com.rmrx.agenticshop.common.dto.Ref;
import com.rmrx.agenticshop.identity.entity.Role;
import com.rmrx.agenticshop.identity.entity.UserStatus;

import java.time.Instant;
import java.util.List;

public final class UserDtos {

    private UserDtos() {
    }

    public record CustomerSummary(Long id, String fullName, String email, UserStatus status, Instant createdAt) {
    }

    public record AddressDto(Long id, String label, String recipient, String line1, String line2, String city,
                             String region, String postalCode, String country, boolean defaultAddress) {
    }

    public record CustomerStats(long orderCount, long returnCount, long ticketCount, long activeFlagCount) {
    }

    public record CustomerDetail(Long id, String fullName, String email, String phone, UserStatus status,
                                 Instant createdAt, List<AddressDto> addresses, CustomerStats stats) {
    }

    /** Staff and seller users: who they are and which role (and seller, for SELLER users) they act as. */
    public record UserDto(Long id, String fullName, String email, Role role, Ref seller, UserStatus status,
                          Instant createdAt) {
    }
}
