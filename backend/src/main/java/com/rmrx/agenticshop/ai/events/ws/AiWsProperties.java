package com.rmrx.agenticshop.ai.events.ws;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * The ai WebSocket ({@code app.ai.ws}).
 *
 * @param allowedOrigins  browser origins allowed to open the socket (requests without an Origin are not browsers)
 * @param connectTokenTtl how long a connect token can be used for the handshake
 * @param maxMessageBytes the largest text frame accepted from a client
 * @param sendTimeLimit   a send blocked longer than this closes the socket (a slow client never blocks a run)
 * @param sendBufferBytes outgoing bytes buffered for a slow client before the socket is closed
 * @param pingInterval    how often the server pings, so proxies keep the connection and dead ones are noticed
 * @param idleTimeout     a socket with no incoming traffic (pongs included) for this long is closed
 */
@ConfigurationProperties("app.ai.ws")
public record AiWsProperties(
        @DefaultValue("http://localhost:4200") List<String> allowedOrigins,
        @DefaultValue("30s") Duration connectTokenTtl,
        @DefaultValue("16384") int maxMessageBytes,
        @DefaultValue("10s") Duration sendTimeLimit,
        @DefaultValue("524288") int sendBufferBytes,
        @DefaultValue("15s") Duration pingInterval,
        @DefaultValue("60s") Duration idleTimeout) {
}
