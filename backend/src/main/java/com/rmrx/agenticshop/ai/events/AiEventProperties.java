package com.rmrx.agenticshop.ai.events;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Live run events of the ai agent ({@code app.ai.events}), in memory, never persisted.
 *
 * @param retention  how long a closed stream stays replayable (a late reconnect still gets the last event)
 * @param bufferSize how many events a run keeps for replay
 * @param heartbeat  how often an open SSE connection gets a heartbeat comment
 * @param maxOpen    a stream still open after this is closed (a hung run); also bounds an SSE connection
 */
@ConfigurationProperties("app.ai.events")
public record AiEventProperties(
        @DefaultValue("5m") Duration retention,
        @DefaultValue("2000") int bufferSize,
        @DefaultValue("15s") Duration heartbeat,
        @DefaultValue("10m") Duration maxOpen) {
}
