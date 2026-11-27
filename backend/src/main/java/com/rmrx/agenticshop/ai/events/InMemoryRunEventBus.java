package com.rmrx.agenticshop.ai.events;

import com.rmrx.agenticshop.ai.contract.reply.ReplyEnvelope;
import com.rmrx.agenticshop.ai.events.ChatResponse.Progress;
import com.rmrx.agenticshop.ai.events.ChatResponse.Stage;
import com.rmrx.agenticshop.ai.graph.agent.pending.PendingInteraction;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
 * Single-node, in-memory {@link RunEventBus} (same design as the {@code ai} package's bus).
 *
 * <p>Ordering: each stream has one lock. Emitting assigns the next seq, appends to the bounded buffer and enqueues the
 * event into every subscriber's mailbox, all under that lock. Subscribing copies the buffered events after the client's
 * last seq into the new mailbox and adds it to the subscriber list under the same lock. So a subscriber sees each seq
 * exactly once and in order, whether it came from the replay or live. Delivery happens outside the lock: each mailbox
 * is drained by at most one virtual thread at a time, so a slow client never blocks the run or other subscribers. A
 * mailbox more than twice bufferSize events behind is dropped (the client can reconnect with its last seq).
 *
 * <p>A user subscription ({@link #subscribeUser}) gets every event of every run the user owns, live only, enqueued
 * under each run's lock like the others (so each run's events stay in order). Runs never close it.
 *
 * <p>A resumed run reuses its run id: {@link #open} then replaces the previous (closed) stream with a new one whose
 * seq continues where the previous stopped.
 *
 * <p>Eviction: a daemon sweeper removes closed streams after the retention period and force-closes streams open
 * longer than {@code maxOpen}; lookups also evict an expired stream lazily.
 */
@Component("aiRunEventBus")
public class InMemoryRunEventBus implements RunEventBus {

    private static final Logger log = LoggerFactory.getLogger(InMemoryRunEventBus.class);
    private static final Object CLOSE = new Object();

    private final ConcurrentHashMap<UUID, Channel> runs = new ConcurrentHashMap<>();
    /** Live subscriptions to all runs of a user (never replayed, never closed by a run). */
    private final ConcurrentHashMap<Long, Set<Mailbox>> users = new ConcurrentHashMap<>();
    private final int bufferSize;
    private final Duration retention;
    private final Duration maxOpen;
    private final ExecutorService delivery = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService sweeper = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("ai-run-event-sweeper").factory());

    public InMemoryRunEventBus(AiEventProperties props) {
        this.bufferSize = Math.max(16, props.bufferSize());
        this.retention = props.retention();
        this.maxOpen = props.maxOpen();
        long sweepMs = Math.clamp(retention.toMillis() / 2, 1_000L, 30_000L);
        sweeper.scheduleWithFixedDelay(this::sweep, sweepMs, sweepMs, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void shutdown() {
        sweeper.shutdownNow();
        delivery.shutdownNow();
    }

    @Override
    public AgentEventPublisher open(UUID runId, Long ownerUserId, Long conversationId) {
        Channel channel = runs.compute(runId, (id, previous) -> {
            if (previous == null) return new Channel(runId, ownerUserId, conversationId, 0);
            previous.forceClose();
            return new Channel(runId, ownerUserId, conversationId, previous.lastSeq());
        });
        return new ChannelPublisher(channel);
    }

    @Override
    public Optional<AgentEventPublisher> publisher(UUID runId) {
        return live(runId).filter(c -> !c.isClosed()).map(ChannelPublisher::new);
    }

    @Override
    public Optional<Long> ownerOf(UUID runId) {
        return live(runId).map(c -> c.owner);
    }

    @Override
    public Optional<RunSubscription> subscribe(UUID runId, long afterSeq, RunEventSubscriber subscriber) {
        return live(runId).map(c -> c.subscribe(afterSeq, subscriber));
    }

    @Override
    public RunSubscription subscribeUser(Long userId, RunEventSubscriber subscriber) {
        Mailbox mailbox = new Mailbox(subscriber, "user " + userId);
        Set<Mailbox> mailboxes = users.computeIfAbsent(userId, id -> ConcurrentHashMap.newKeySet());
        mailbox.onCancel = () -> mailboxes.remove(mailbox);
        mailboxes.add(mailbox);
        return mailbox;
    }

    /** The stream unless it is unknown or expired (lazy eviction). */
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
                    log.warn("Run {} still streaming after {}; closing its event stream", channel.runId, maxOpen);
                    channel.forceClose();
                }
            });
        } catch (RuntimeException e) {
            log.error("Run event sweep failed", e);
        }
    }

    /** One stream of a run: buffer, subscribers and the closed flag, guarded by one lock. */
    private final class Channel {

        final UUID runId;
        final Long owner;
        final Long conversationId;
        final Instant createdAt = Instant.now();
        private final ReentrantLock lock = new ReentrantLock();
        private final ArrayDeque<ChatResponse> buffer = new ArrayDeque<>();
        private final List<Mailbox> subscribers = new ArrayList<>();
        private long lastSeq;
        private boolean closed;
        private volatile Instant closedAt;

        Channel(UUID runId, Long owner, Long conversationId, long lastSeq) {
            this.runId = runId;
            this.owner = owner;
            this.conversationId = conversationId;
            this.lastSeq = lastSeq;
        }

        /** Emits the event built for the next seq; with close, also closes the stream. False if already closed. */
        boolean emit(LongFunction<ChatResponse> factory, boolean close) {
            lock.lock();
            try {
                if (closed) return false;
                ChatResponse event = factory.apply(++lastSeq);
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
                Set<Mailbox> ownerMailboxes = users.get(owner);
                if (ownerMailboxes != null) {
                    for (Mailbox mailbox : ownerMailboxes) {
                        if (!mailbox.offer(event)) {
                            log.debug("Subscriber of user {} fell behind; dropped", owner);
                            ownerMailboxes.remove(mailbox);
                            mailbox.offerClose();
                        }
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
            Mailbox mailbox = new Mailbox(subscriber, "run " + runId);
            mailbox.onCancel = () -> remove(mailbox);
            lock.lock();
            try {
                for (ChatResponse event : buffer) {
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

        long lastSeq() {
            lock.lock();
            try {
                return lastSeq;
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
            return closedAt == null && createdAt.plus(maxOpen).isBefore(now);
        }
    }

    /**
     * One subscriber's FIFO of pending events (of one run, or of every run of one user). Items are added under the
     * emitting stream's lock (so each run's events are in seq order); at most one delivery task drains it at a time,
     * outside any lock.
     */
    private final class Mailbox implements RunSubscription {

        private final RunEventSubscriber subscriber;
        private final String name;
        private final ConcurrentLinkedQueue<Object> queue = new ConcurrentLinkedQueue<>();
        private final AtomicInteger pending = new AtomicInteger();
        private final AtomicBoolean scheduled = new AtomicBoolean();
        /** Detaches the mailbox from what feeds it. */
        private Runnable onCancel = () -> { };
        private volatile boolean dead;

        Mailbox(RunEventSubscriber subscriber, String name) {
            this.subscriber = subscriber;
            this.name = name;
        }

        /** Replayed events are never refused; the buffer bounds them. */
        void replay(ChatResponse event) {
            pending.incrementAndGet();
            queue.add(event);
        }

        /** False if the subscriber is too far behind and should be dropped. */
        boolean offer(ChatResponse event) {
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
                            subscriber.onEvent((ChatResponse) item);
                        }
                    } catch (RuntimeException e) {
                        log.debug("Subscriber of {} failed and was removed: {}", name, e.toString());
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
            onCancel.run();
        }
    }

    /** The publisher of one stream, handed to agent code. */
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
        public void input(String requestId, ChatResponse.Input input) {
            emit("INPUT", seq -> ChatResponse.input(runId(), channel.conversationId, seq, requestId, input), false);
        }

        @Override
        public void toolStarted(String toolName, String progressLabel) {
            progress(new Progress(Stage.TOOL_STARTED, progressLabel, toolName, null, null));
        }

        @Override
        public void toolFinished(String toolName, boolean success, long durationMs) {
            progress(new Progress(Stage.TOOL_FINISHED, null, toolName, success, durationMs));
        }

        @Override
        public void notice(Stage stage, String label) {
            progress(new Progress(stage, label, null, null, null));
        }

        @Override
        public void complete(ReplyEnvelope reply) {
            emit("REPLY", seq -> ChatResponse.reply(runId(), channel.conversationId, seq, reply), true);
        }

        @Override
        public void pause(PendingInteraction interaction) {
            emit("INTERACTION", seq -> ChatResponse.interaction(runId(), channel.conversationId, seq, interaction),
                    true);
        }

        @Override
        public void fail(ChatResponse.ErrorCode code, String message, boolean retryable) {
            emit("ERROR", seq -> ChatResponse.error(runId(), channel.conversationId, seq,
                    new ChatResponse.Error(code, message, retryable)), true);
        }

        @Override
        public boolean isClosed() {
            return channel.isClosed();
        }

        private void progress(Progress progress) {
            emit(progress.stage().name(), seq -> ChatResponse.progress(runId(), channel.conversationId, seq, progress),
                    false);
        }

        private void emit(String what, LongFunction<ChatResponse> factory, boolean close) {
            if (!channel.emit(factory, close)) {
                log.debug("Run {} stream is closed; ignored {}", channel.runId, what);
            }
        }
    }
}
