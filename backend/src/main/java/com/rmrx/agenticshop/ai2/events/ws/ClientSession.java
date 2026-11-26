package com.rmrx.agenticshop.ai2.events.ws;

import com.rmrx.agenticshop.ai2.events.ChatResponse;
import com.rmrx.agenticshop.ai2.events.RunEventBus;
import com.rmrx.agenticshop.ai2.events.RunEventSubscriber;
import com.rmrx.agenticshop.ai2.events.RunSubscription;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * One open socket: sends {@link ChatResponse}s (thread-safe, bounded by the send time and buffer limits) and follows
 * the events of runs for it, one bus subscription per run. It pings the client, closes itself when the client stops
 * answering, and when the user's JWT expires. Closing only stops delivery; the runs go on.
 */
final class ClientSession {

    static final CloseStatus EXPIRED = CloseStatus.POLICY_VIOLATION
            .withReason("Session expired; reconnect with a new connect token");
    static final CloseStatus IDLE = CloseStatus.SESSION_NOT_RELIABLE.withReason("No answer to pings");
    private static final Logger log = LoggerFactory.getLogger(ClientSession.class);

    private final WebSocketSession session;
    private final WsUser user;
    private final ObjectWriter writer;
    private final RunEventBus bus;
    private final Map<UUID, Follower> following = new ConcurrentHashMap<>();
    private final Duration pingInterval;
    private final Duration idleTimeout;
    private volatile List<ScheduledFuture<?>> timers = List.of();
    private volatile Instant lastIncoming = Instant.now();
    private volatile boolean closed;

    ClientSession(WebSocketSession raw, WsUser user, ObjectWriter writer, RunEventBus bus, Ai2WsProperties props) {
        this.session = new ConcurrentWebSocketSessionDecorator(raw, (int) props.sendTimeLimit().toMillis(),
                props.sendBufferBytes());
        this.user = user;
        this.writer = writer;
        this.bus = bus;
        this.pingInterval = props.pingInterval();
        this.idleTimeout = props.idleTimeout();
    }

    /** Schedules the expiry close and the pings; the work runs on io, never on the scheduler thread. */
    void start(ScheduledExecutorService scheduler, ExecutorService io) {
        long untilExpiry = Math.max(0, Duration.between(Instant.now(), user.expiresAt()).toMillis());
        long pingMs = pingInterval.toMillis();
        timers = List.of(
                scheduler.schedule(() -> io.execute(() -> close(EXPIRED)), untilExpiry, TimeUnit.MILLISECONDS),
                scheduler.scheduleAtFixedRate(() -> io.execute(this::ping), pingMs, pingMs, TimeUnit.MILLISECONDS));
        if (closed) release();
    }

    Long userId() {
        return user.user().id();
    }

    /** Sends one frame; a failure closes the socket and is rethrown (unchecked). */
    void send(ChatResponse message) {
        try {
            session.sendMessage(new TextMessage(writer.writeValueAsString(message)));
        } catch (IOException | RuntimeException e) {
            log.debug("Send to ai2 socket {} failed: {}", session.getId(), e.toString());
            close(CloseStatus.SESSION_NOT_RELIABLE);
            throw e instanceof IOException io ? new UncheckedIOException(io) : (RuntimeException) e;
        }
    }

    /** An ERROR answering the client's request (not retryable unless said otherwise). */
    void refuse(String requestId, ChatResponse.ErrorCode code, String message) {
        boolean retryable = code == ChatResponse.ErrorCode.CONFLICT || code == ChatResponse.ErrorCode.INTERNAL;
        send(ChatResponse.refused(requestId, null, null, code, message, retryable));
    }

    /**
     * Follows the run's events after afterSeq (replacing an earlier subscription to the same run). False when the run
     * is not streamed here.
     */
    boolean follow(UUID runId, long afterSeq) {
        Follower follower = new Follower(runId);
        Follower previous = following.put(runId, follower);
        if (previous != null) previous.cancel();
        var subscription = bus.subscribe(runId, afterSeq, follower);
        if (subscription.isEmpty()) {
            following.remove(runId, follower);
            return false;
        }
        follower.attach(subscription.get());
        if (closed) follower.cancel();
        return true;
    }

    /** Closes the socket (if still open) and stops everything. */
    void close(CloseStatus status) {
        release();
        try {
            if (session.isOpen()) session.close(status);
        } catch (IOException e) {
            log.debug("Closing ai2 socket {} failed: {}", session.getId(), e.toString());
        }
    }

    /** The socket is closed: stops the timers and every subscription. Idempotent. */
    void release() {
        closed = true;
        timers.forEach(t -> t.cancel(false));
        following.values().forEach(Follower::cancel);
    }

    /** The client sent something (a frame or a pong). */
    void touch() {
        lastIncoming = Instant.now();
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
            log.debug("Ping to ai2 socket {} failed: {}", session.getId(), e.toString());
            close(CloseStatus.SESSION_NOT_RELIABLE);
        }
    }

    /** This socket's subscription to one run. */
    private final class Follower implements RunEventSubscriber {

        private final UUID runId;
        private volatile RunSubscription subscription;
        private volatile boolean cancelled;

        Follower(UUID runId) {
            this.runId = runId;
        }

        void attach(RunSubscription subscription) {
            this.subscription = subscription;
            if (cancelled) subscription.cancel();
        }

        @Override
        public void onEvent(ChatResponse event) {
            if (closed || cancelled) throw new IllegalStateException("not following run " + runId);
            send(event);
        }

        @Override
        public void onClose() {
            // only this subscription: a resumed run may already have a newer one
            following.remove(runId, this);
        }

        void cancel() {
            cancelled = true;
            following.remove(runId, this);
            RunSubscription s = subscription;
            if (s != null) s.cancel();
        }
    }
}
