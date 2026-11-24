package com.rmrx.agenticshop.ai.events;

import com.rmrx.agenticshop.ai.config.AiProperties;
import com.rmrx.agenticshop.ai.contract.block.Block;
import com.rmrx.agenticshop.ai.contract.event.AgentEvent;
import com.rmrx.agenticshop.ai.contract.response.AgentResponse;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongFunction;

/**
 * Single-node, in-memory {@link RunEventBus}.
 *
 * <p>Ordering: each run has one lock. Emitting assigns the next seq, appends to the run's bounded buffer and
 * enqueues the event into every subscriber's mailbox, all under that lock. Subscribing copies the buffered events
 * after the client's last seq into the new mailbox and adds it to the subscriber list under the same lock. So a
 * subscriber sees each seq exactly once and in order, whether it came from the replay or live. Delivery itself
 * happens outside the lock: each mailbox is drained by at most one virtual thread at a time, so a slow client never
 * blocks the run or other subscribers. A mailbox that falls more than twice bufferSize events behind is dropped (the
 * client can reconnect with its last seq). When a run emits more than bufferSize events, the oldest are dropped from
 * the buffer, so only a late subscriber resuming from before them sees a gap.
 *
 * <p>Eviction: a daemon sweeper removes closed runs after the retention period and force-closes runs left open
 * longer than the turn timeout plus a margin; lookups also evict an expired run lazily.
 */
@Slf4j
@Component
public class InMemoryRunEventBus implements RunEventBus {

    private static final Object CLOSE = new Object();
    private static final Duration OPEN_MARGIN = Duration.ofMinutes(1);

    private final ConcurrentHashMap<UUID, Channel> runs = new ConcurrentHashMap<>();
    private final int bufferSize;
    private final Duration retention;
    private final Duration maxOpenAge;
    private final ExecutorService delivery = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService sweeper = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("run-event-sweeper").factory());

    public InMemoryRunEventBus(AiProperties props) {
        AiProperties.Events events = props.events();
        this.bufferSize = Math.max(16, events.bufferSize());
        this.retention = events.retention();
        this.maxOpenAge = props.agent().turnTimeout().plus(OPEN_MARGIN);
        long sweepMs = Math.clamp(retention.toMillis() / 2, 1_000L, 30_000L);
        sweeper.scheduleWithFixedDelay(this::sweep, sweepMs, sweepMs, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void shutdown() {
        sweeper.shutdownNow();
        delivery.shutdownNow();
    }

    @Override
    public AgentEventPublisher open(UUID runId, Long ownerUserId) {
        Channel channel = new Channel(runId, ownerUserId);
        if (runs.putIfAbsent(runId, channel) != null) {
            throw new IllegalStateException("Run " + runId + " is already registered");
        }
        return new ChannelPublisher(channel);
    }

    @Override
    public Optional<Long> ownerOf(UUID runId) {
        return live(runId).map(c -> c.owner);
    }

    @Override
    public Optional<RunSubscription> subscribe(UUID runId, long afterSeq, RunEventSubscriber subscriber) {
        return live(runId).map(c -> c.subscribe(afterSeq, subscriber));
    }

    /** The channel unless it is unknown or expired (lazy eviction). */
    private Optional<Channel> live(UUID runId) {
        Channel channel = runs.get(runId);
        if (channel == null) return Optional.empty();
        if (channel.expired(Instant.now())) {
            runs.remove(runId, channel);
            return Optional.empty();
        }
        return Optional.of(channel);
    }

    void sweep() {
        try {
            Instant now = Instant.now();
            runs.values().forEach(channel -> {
                if (channel.expired(now)) {
                    runs.remove(channel.runId, channel);
                    log.debug("Evicted events of run {}", channel.runId);
                } else if (channel.openTooLong(now)) {
                    log.warn("Run {} still open after {}; closing its event stream", channel.runId, maxOpenAge);
                    channel.forceClose();
                }
            });
        } catch (RuntimeException e) {
            log.error("Run event sweep failed", e);
        }
    }

    /** One run: buffer, subscribers and the closed flag, guarded by one lock. */
    private final class Channel {

        final UUID runId;
        final Long owner;
        final Instant createdAt = Instant.now();
        private final ReentrantLock lock = new ReentrantLock();
        private final ArrayDeque<AgentEvent> buffer = new ArrayDeque<>();
        private final List<Mailbox> subscribers = new ArrayList<>();
        private long lastSeq;
        private boolean closed;
        private volatile Instant closedAt;

        Channel(UUID runId, Long owner) {
            this.runId = runId;
            this.owner = owner;
        }

        /** Emits the event built for the next seq; with close, also closes the run. False if already closed. */
        boolean emit(LongFunction<AgentEvent> factory, boolean close) {
            lock.lock();
            try {
                if (closed) return false;
                AgentEvent event = factory.apply(++lastSeq);
                if (buffer.size() >= bufferSize) buffer.pollFirst();
                buffer.addLast(event);
                for (int i = subscribers.size() - 1; i >= 0; i--) {
                    Mailbox mailbox = subscribers.get(i);
                    if (!mailbox.offer(event)) {
                        log.debug("Subscriber of run {} fell behind; dropped", runId);
                        subscribers.remove(i);
                        mailbox.offerClose();
                    }
                }
                if (close) closeLocked();
                return true;
            } finally {
                lock.unlock();
            }
        }

        void forceClose() {
            lock.lock();
            try {
                if (!closed) closeLocked();
            } finally {
                lock.unlock();
            }
        }

        private void closeLocked() {
            closed = true;
            closedAt = Instant.now();
            subscribers.forEach(Mailbox::offerClose);
            subscribers.clear();
        }

        RunSubscription subscribe(long afterSeq, RunEventSubscriber subscriber) {
            Mailbox mailbox = new Mailbox(this, subscriber);
            lock.lock();
            try {
                for (AgentEvent event : buffer) {
                    if (event.seq() > afterSeq) mailbox.replay(event);
                }
                if (closed) mailbox.offerClose();
                else subscribers.add(mailbox);
                mailbox.schedule();
            } finally {
                lock.unlock();
            }
            return mailbox;
        }

        void remove(Mailbox mailbox) {
            lock.lock();
            try {
                subscribers.remove(mailbox);
            } finally {
                lock.unlock();
            }
        }

        boolean isClosed() {
            lock.lock();
            try {
                return closed;
            } finally {
                lock.unlock();
            }
        }

        boolean expired(Instant now) {
            Instant at = closedAt;
            return at != null && at.plus(retention).isBefore(now);
        }

        boolean openTooLong(Instant now) {
            return closedAt == null && createdAt.plus(maxOpenAge).isBefore(now);
        }
    }

    /**
     * One subscriber's FIFO of pending events. Items are added under the channel lock (so in seq order); at most
     * one delivery task drains it at a time, outside any lock.
     */
    private final class Mailbox implements RunSubscription {

        private final Channel channel;
        private final RunEventSubscriber subscriber;
        private final ConcurrentLinkedQueue<Object> queue = new ConcurrentLinkedQueue<>();
        private final AtomicInteger pending = new AtomicInteger();
        private final AtomicBoolean scheduled = new AtomicBoolean();
        private volatile boolean dead;

        Mailbox(Channel channel, RunEventSubscriber subscriber) {
            this.channel = channel;
            this.subscriber = subscriber;
        }

        /** Replayed events are never refused; the buffer bounds them. */
        void replay(AgentEvent event) {
            pending.incrementAndGet();
            queue.add(event);
        }

        /** False if the subscriber is too far behind and should be dropped. */
        boolean offer(AgentEvent event) {
            if (dead) return true;
            // a replay may already have queued up to bufferSize events
            if (pending.incrementAndGet() > 2 * bufferSize) return false;
            queue.add(event);
            schedule();
            return true;
        }

        void offerClose() {
            queue.add(CLOSE);
            schedule();
        }

        void schedule() {
            if (!queue.isEmpty() && scheduled.compareAndSet(false, true)) {
                try {
                    delivery.execute(this::drain);
                } catch (RuntimeException e) { // executor shut down
                    scheduled.set(false);
                }
            }
        }

        private void drain() {
            while (true) {
                Object item;
                while ((item = queue.poll()) != null) {
                    if (dead) {
                        queue.clear();
                        break;
                    }
                    try {
                        if (item == CLOSE) {
                            dead = true;
                            subscriber.onClose();
                        } else {
                            pending.decrementAndGet();
                            subscriber.onEvent((AgentEvent) item);
                        }
                    } catch (RuntimeException e) {
                        log.debug("Subscriber of run {} failed and was removed: {}", channel.runId, e.toString());
                        cancel();
                    }
                }
                scheduled.set(false);
                // an item added after the last poll but before the flag reset would otherwise be stranded
                if (queue.isEmpty() || !scheduled.compareAndSet(false, true)) return;
            }
        }

        @Override
        public void cancel() {
            dead = true;
            channel.remove(this);
        }
    }

    /** The per-run publisher handed to agent code. */
    private static final class ChannelPublisher implements AgentEventPublisher {

        private final Channel channel;

        ChannelPublisher(Channel channel) {
            this.channel = channel;
        }

        @Override
        public UUID runId() {
            return channel.runId;
        }

        @Override
        public void runStarted(Long ticketId) {
            emit("RUN_STARTED", seq -> new AgentEvent.RunStarted(runId(), seq, Instant.now(), ticketId), false);
        }

        @Override
        public void tokenDelta(String text) {
            emit("TOKEN_DELTA", seq -> new AgentEvent.TokenDelta(runId(), seq, Instant.now(), text), false);
        }

        @Override
        public void toolStarted(String toolName, String progressLabel) {
            emit("TOOL_STARTED", seq -> new AgentEvent.ToolStarted(runId(), seq, Instant.now(), toolName,
                    progressLabel), false);
        }

        @Override
        public void toolFinished(String toolName, boolean success, String errorCode, long durationMs) {
            emit("TOOL_FINISHED", seq -> new AgentEvent.ToolFinished(runId(), seq, Instant.now(), toolName,
                    success, errorCode, durationMs), false);
        }

        @Override
        public void blockReady(Block block) {
            emit("BLOCK_READY", seq -> new AgentEvent.BlockReady(runId(), seq, Instant.now(), block), false);
        }

        @Override
        public void complete(AgentResponse response) {
            emit("FINAL", seq -> new AgentEvent.Final(runId(), seq, Instant.now(), response), true);
        }

        @Override
        public void fail(String code, String message, boolean retryable) {
            emit("ERROR", seq -> new AgentEvent.Error(runId(), seq, Instant.now(), code, message, retryable),
                    true);
        }

        @Override
        public boolean isClosed() {
            return channel.isClosed();
        }

        private void emit(String type, LongFunction<AgentEvent> factory, boolean close) {
            if (!channel.emit(factory, close)) {
                log.debug("Run {} is closed; ignored {}", channel.runId, type);
            }
        }
    }
}
