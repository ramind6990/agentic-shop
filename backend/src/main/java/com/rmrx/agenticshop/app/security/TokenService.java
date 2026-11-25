package com.rmrx.agenticshop.app.security;

import com.rmrx.agenticshop.app.identity.entity.AppUser;
import com.rmrx.agenticshop.app.identity.entity.Role;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Instant;

/** Issues access tokens and reads their claims back into a {@link CurrentUser}. */
@Service
public class TokenService {

    private static final String USERNAME = "username";
    private static final String ROLE = "role";
    private static final String SELLER_ID = "sellerId";

    private final JwtEncoder encoder;
    private final JwtProperties properties;

    public TokenService(JwtEncoder encoder, JwtProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }

    public IssuedToken issue(AppUser user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.ttl());
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("agenticshop")
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(USERNAME, user.getUsername())
                .claim(ROLE, user.getRole().name());
        if (user.getSeller() != null) claims.claim(SELLER_ID, user.getSeller().getId());
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }

    public static CurrentUser toCurrentUser(Jwt jwt) {
        Object sellerId = jwt.getClaim(SELLER_ID);
        return new CurrentUser(Long.valueOf(jwt.getSubject()), jwt.getClaimAsString(USERNAME),
                Role.valueOf(jwt.getClaimAsString(ROLE)),
                sellerId == null ? null : ((Number) sellerId).longValue());
    }
}
