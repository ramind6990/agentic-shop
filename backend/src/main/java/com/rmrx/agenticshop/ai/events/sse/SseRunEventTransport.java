package com.rmrx.agenticshop.ai.events.sse;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.rmrx.agenticshop.ai.config.AiProperties;
import com.rmrx.agenticshop.ai.contract.event.AgentEvent;
import com.rmrx.agenticshop.ai.events.RunEventBus;
import com.rmrx.agenticshop.ai.events.RunEventSubscriber;
import com.rmrx.agenticshop.ai.events.RunSubscription;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Streams a run's events as Server-Sent Events. Frames: {@code id:} = seq, {@code event:} = the type discriminator
 * (RUN_STARTED, TOKEN_DELTA, …), {@code data:} = the event JSON with its {@code type} field. The stream completes
 * after FINAL or ERROR; a heartbeat comment keeps idle connections open. A client disconnect only unsubscribes; the
 * run goes on. Access checks are the caller's job.
 */
@Slf4j
@Component
public class SseRunEventTransport {

    public static final String RUN_NOT_LIVE = "RUN_NOT_LIVE";
    private static final Duration TIMEOUT_MARGIN = Duration.ofSeconds(30);

    private final RunEventBus bus;
    private final ObjectWriter writer;
    private final Map<Class<?>, String> typeNames = new HashMap<>();
    private final long timeoutMs;
    private final long heartbeatMs;
    private final ScheduledExecutorService heartbeats = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("sse-heartbeat").factory());
    /** Heartbeat writes may block on a slow client; they run here, not on the scheduler thread. */
    private final ExecutorService writes = Executors.newVirtualThreadPerTaskExecutor();

    public SseRunEventTransport(RunEventBus bus, JsonMapper mapper, AiProperties props) {
        this.bus = bus;
        this.writer = mapper.writerFor(AgentEvent.class);
        this.timeoutMs = props.agent().turnTimeout().plus(TIMEOUT_MARGIN).toMillis();
        this.heartbeatMs = Math.max(1_000L, props.events().heartbeat().toMillis());
        for (JsonSubTypes.Type t : AgentEvent.class.getAnnotation(JsonSubTypes.class).value()) {
            typeNames.put(t.value(), t.name());
        }
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

    /** A one-event stream telling the client the run is no longer streamed (finished and evicted, or elsewhere). */
    public SseEmitter notLive(UUID runId) {
        SseEmitter emitter = new SseEmitter(timeoutMs);
        AgentEvent error = new AgentEvent.Error(runId, 0, Instant.now(), RUN_NOT_LIVE,
                "This run is no longer streaming; load the conversation instead.", false);
        try {
            emitter.send(frame(error));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }

    private SseEmitter.SseEventBuilder frame(AgentEvent event) {
        return SseEmitter.event()
                .id(Long.toString(event.seq()))
                .name(typeNames.getOrDefault(event.getClass(), "EVENT"))
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
        public void onEvent(AgentEvent event) {
            lock.lock();
            try {
                if (done) throw new IllegalStateException("stream closed");
                emitter.send(frame(event));
                if (event instanceof AgentEvent.Final || event instanceof AgentEvent.Error) finishLocked();
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
