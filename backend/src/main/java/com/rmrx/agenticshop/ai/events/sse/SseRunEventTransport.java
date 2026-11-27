package com.rmrx.agenticshop.ai.events.sse;

import com.rmrx.agenticshop.ai.events.AiEventProperties;
import com.rmrx.agenticshop.ai.events.ChatResponse;
import com.rmrx.agenticshop.ai.events.RunEventBus;
import com.rmrx.agenticshop.ai.events.RunEventSubscriber;
import com.rmrx.agenticshop.ai.events.RunSubscription;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Streams a run's events as Server-Sent Events. Frames: {@code id:} = seq, {@code event:} = the {@link ChatResponse}
 * type (REPLY, INTERACTION, PROGRESS, ERROR), {@code data:} = the {@link ChatResponse} JSON. The stream completes after
 * the first non-PROGRESS event; a heartbeat comment keeps idle connections open. A client disconnect only
 * unsubscribes; the run goes on. Access checks are the caller's job.
 */
@Component("aiSseRunEventTransport")
public class SseRunEventTransport {

    public static final String NOT_LIVE_MESSAGE = "This run is no longer streaming; load the conversation instead.";
    private static final Logger log = LoggerFactory.getLogger(SseRunEventTransport.class);
    private static final Duration TIMEOUT_MARGIN = Duration.ofSeconds(30);

    private final RunEventBus bus;
    private final ObjectWriter writer;
    private final long timeoutMs;
    private final long heartbeatMs;
    private final ScheduledExecutorService heartbeats = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("ai-sse-heartbeat").factory());
    /** Heartbeat writes may block on a slow client; they run here, not on the scheduler thread. */
    private final ExecutorService writes = Executors.newVirtualThreadPerTaskExecutor();

    public SseRunEventTransport(RunEventBus bus, JsonMapper mapper, AiEventProperties props) {
        this.bus = bus;
        this.writer = mapper.writerFor(ChatResponse.class);
        this.timeoutMs = props.maxOpen().plus(TIMEOUT_MARGIN).toMillis();
        this.heartbeatMs = Math.max(1_000L, props.heartbeat().toMillis());
    }

    @PreDestroy
    void shutdown() {
        heartbeats.shutdownNow();
        writes.shutdownNow();
    }

    /** Replays events after afterSeq, then streams live ones; a not-live stream if the run vanished meanwhile. */
    public SseEmitter stream(UUID runId, long afterSeq) {
        SseEmitter emitter = new SseEmitter(timeoutMs);
        Stream stream = new Stream(runId, emitter);
        emitter.onCompletion(stream::release);
        emitter.onTimeout(stream::release);
        emitter.onError(e -> stream.release());
        Optional<RunSubscription> subscription = bus.subscribe(runId, afterSeq, stream);
        if (subscription.isEmpty()) {
            stream.release();
            return notLive(runId);
        }
        stream.attach(subscription.get(), heartbeats.scheduleAtFixedRate(
                () -> writes.execute(stream::heartbeat), heartbeatMs, heartbeatMs, TimeUnit.MILLISECONDS));
        return emitter;
    }

    /** A one-event stream telling the client the run is not streamed (finished and evicted, or unknown). */
    public SseEmitter notLive(UUID runId) {
        SseEmitter emitter = new SseEmitter(timeoutMs);
        ChatResponse error = ChatResponse.error(runId, null, 0L, new ChatResponse.Error(
                ChatResponse.ErrorCode.RUN_NOT_LIVE, NOT_LIVE_MESSAGE, false));
        try {
            emitter.send(frame(error));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }

    private SseEmitter.SseEventBuilder frame(ChatResponse event) {
        return SseEmitter.event()
                .id(String.valueOf(event.seq()))
                .name(event.type().name())
                .data(writer.writeValueAsString(event));
    }

    /** One open SSE connection subscribed to one run. */
    private final class Stream implements RunEventSubscriber {

        private final UUID runId;
        private final SseEmitter emitter;
        private final ReentrantLock lock = new ReentrantLock();
        private boolean done;
        private RunSubscription subscription;
        private ScheduledFuture<?> heartbeat;

        Stream(UUID runId, SseEmitter emitter) {
            this.runId = runId;
            this.emitter = emitter;
        }

        void attach(RunSubscription subscription, ScheduledFuture<?> heartbeat) {
            lock.lock();
            try {
                this.subscription = subscription;
                this.heartbeat = heartbeat;
                if (done) cleanupLocked();
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void onEvent(ChatResponse event) {
            lock.lock();
            try {
                if (done) throw new IllegalStateException("stream closed");
                emitter.send(frame(event));
                if (event.last()) finishLocked();
            } catch (IOException e) {
                // client went away; the container reports it via onError/onCompletion as well
                done = true;
                cleanupLocked();
                throw new UncheckedIOException(e);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void onClose() {
            lock.lock();
            try {
                if (!done) finishLocked();
            } finally {
                lock.unlock();
            }
        }

        void heartbeat() {
            lock.lock();
            try {
                if (done) return;
                emitter.send(SseEmitter.event().comment("hb"));
            } catch (IOException | IllegalStateException e) {
                log.debug("Heartbeat to run {} stream failed: {}", runId, e.toString());
                done = true;
                cleanupLocked();
            } finally {
                lock.unlock();
            }
        }

        /** Emitter completed, timed out or failed: stop everything without touching the run. */
        void release() {
            lock.lock();
            try {
                done = true;
                cleanupLocked();
            } finally {
                lock.unlock();
            }
        }

        private void finishLocked() {
            done = true;
            cleanupLocked();
            emitter.complete();
        }

        private void cleanupLocked() {
            if (heartbeat != null) heartbeat.cancel(false);
            if (subscription != null) subscription.cancel();
        }
    }
}
