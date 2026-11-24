package com.rmrx.agenticshop.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** {@code app.jwt.*}: HS256 signing secret (at least 32 bytes) and access token lifetime. */
@ConfigurationProperties("app.jwt")
public record JwtProperties(String secret, Duration ttl) {
}
