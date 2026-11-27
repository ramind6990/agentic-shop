package com.rmrx.agenticshop.ai.events.ws;

import com.rmrx.agenticshop.app.security.CurrentUser;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One-time connect tokens for the WebSocket handshake (browsers cannot send an Authorization header on a WebSocket).
 * A signed-in user gets one over REST and puts it in the socket URL; it is valid for {@code connectTokenTtl} and
 * consumed by the first handshake that uses it, so the JWT itself never travels on the socket or in a URL. In memory
 * (single instance, like the run events).
 */
@Component
public class WsConnectTokenStore {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ConcurrentHashMap<String, Entry> tokens = new ConcurrentHashMap<>();
    private final AiWsProperties props;

    public WsConnectTokenStore(AiWsProperties props) {
        this.props = props;
    }

    public record Issued(String connectToken, Instant expiresAt) {
    }

    private record Entry(WsUser user, Instant expiresAt) {
    }

    /** A new connect token for the user; jwtExpiresAt bounds the lifetime of the socket opened with it. */
    public Issued issue(CurrentUser user, Instant jwtExpiresAt) {
        Instant now = Instant.now();
        tokens.values().removeIf(e -> e.expiresAt().isBefore(now));
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = now.plus(props.connectTokenTtl());
        tokens.put(token, new Entry(new WsUser(user, jwtExpiresAt), expiresAt));
        return new Issued(token, expiresAt);
    }

    /** The token's user if the token exists and has not expired; the token is gone afterwards either way. */
    public Optional<WsUser> consume(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        Entry entry = tokens.remove(token);
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) return Optional.empty();
        return Optional.of(entry.user());
    }
}
