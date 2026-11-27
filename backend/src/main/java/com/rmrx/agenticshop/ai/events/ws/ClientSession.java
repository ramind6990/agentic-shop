package com.rmrx.agenticshop.ai.events.ws;

import com.rmrx.agenticshop.ai.events.ChatResponse;
import com.rmrx.agenticshop.ai.events.RunEventBus;
import com.rmrx.agenticshop.ai.events.RunEventSubscriber;
import com.rmrx.agenticshop.ai.events.RunSubscription;
import com.rmrx.agenticshop.app.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import tools.jackson.databind.ObjectWriter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * One open socket of a user: gets every event of every run of that user (all the user's sockets do, e.g. two devices)
 * and sends {@link ChatResponse}s (thread-safe, bounded by the send time and buffer limits). It pings the client,
 * closes itself when the client stops answering, and when the user's JWT expires. Closing only stops delivery; the
 * runs go on.
 */
final class ClientSession implements RunEventSubscriber {

    static final CloseStatus EXPIRED = CloseStatus.POLICY_VIOLATION
            .withReason("Session expired; reconnect with a new connect token");
    static final CloseStatus IDLE = CloseStatus.SESSION_NOT_RELIABLE.withReason("No answer to pings");
    private static final Logger log = LoggerFactory.getLogger(ClientSession.class);

    private final WebSocketSession session;
    private final WsUser user;
    private final ObjectWriter writer;
    private final Duration pingInterval;
    private final Duration idleTimeout;
    private volatile RunSubscription subscription;
    private volatile List<ScheduledFuture<?>> timers = List.of();
    private volatile Instant lastIncoming = Instant.now();
    private volatile boolean closed;

    ClientSession(WebSocketSession raw, WsUser user, ObjectWriter writer, AiWsProperties props) {
        this.session = new ConcurrentWebSocketSessionDecorator(raw, (int) props.sendTimeLimit().toMillis(),
                props.sendBufferBytes());
        this.user = user;
        this.writer = writer;
        this.pingInterval = props.pingInterval();
        this.idleTimeout = props.idleTimeout();
    }

    /** Subscribes to the user's events, schedules the expiry close and the pings (their work runs on io). */
    void start(RunEventBus bus, ScheduledExecutorService scheduler, ExecutorService io) {
        subscription = bus.subscribeUser(userId(), this);
        long untilExpiry = Math.max(0, Duration.between(Instant.now(), user.expiresAt()).toMillis());
        long pingMs = pingInterval.toMillis();
        timers = List.of(
                scheduler.schedule(() -> io.execute(() -> close(EXPIRED)), untilExpiry, TimeUnit.MILLISECONDS),
                scheduler.scheduleAtFixedRate(() -> io.execute(this::ping), pingMs, pingMs, TimeUnit.MILLISECONDS));
        if (closed) release();
    }

    CurrentUser user() {
        return user.user();
    }

    Long userId() {
        return user.user().id();
    }

    @Override
    public void onEvent(ChatResponse event) {
        if (closed) throw new IllegalStateException("socket closed");
        send(event);
    }

    /** The bus dropped this socket (too far behind): close it, the client reconnects and reloads. */
    @Override
    public void onClose() {
        close(CloseStatus.SESSION_NOT_RELIABLE);
    }

    /** Sends one frame; a failure closes the socket and is rethrown (unchecked). */
    void send(ChatResponse message) {
        try {
            session.sendMessage(new TextMessage(writer.writeValueAsString(message)));
        } catch (IOException | RuntimeException e) {
            log.debug("Send to ai socket {} failed: {}", session.getId(), e.toString());
            close(CloseStatus.SESSION_NOT_RELIABLE);
            throw e instanceof IOException io ? new UncheckedIOException(io) : (RuntimeException) e;
        }
    }

    /** An ERROR answering the client's request, to this socket only (not retryable unless said otherwise). */
    void refuse(String requestId, ChatResponse.ErrorCode code, String message) {
        boolean retryable = code == ChatResponse.ErrorCode.CONFLICT || code == ChatResponse.ErrorCode.INTERNAL;
        send(ChatResponse.refused(requestId, null, null, code, message, retryable));
    }

    /** The client sent something (a frame or a pong). */
    void touch() {
        lastIncoming = Instant.now();
    }

    /** Closes the socket (if still open) and stops everything. */
    void close(CloseStatus status) {
        release();
        try {
            if (session.isOpen()) session.close(status);
        } catch (IOException e) {
            log.debug("Closing ai socket {} failed: {}", session.getId(), e.toString());
        }
    }

    /** The socket is closed: stops the timers and the subscription. Idempotent. */
    void release() {
        closed = true;
        timers.forEach(t -> t.cancel(false));
        RunSubscription s = subscription;
        if (s != null) s.cancel();
    }

    /** Pings the client; one that has not answered (or sent anything) within idleTimeout is closed as dead. */
    private void ping() {
        if (closed) return;
        if (lastIncoming.plus(idleTimeout).isBefore(Instant.now())) {
            close(IDLE);
            return;
        }
        try {
            session.sendMessage(new PingMessage());
        } catch (IOException | RuntimeException e) {
            log.debug("Ping to ai socket {} failed: {}", session.getId(), e.toString());
            close(CloseStatus.SESSION_NOT_RELIABLE);
        }
    }
}
