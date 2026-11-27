package com.rmrx.agenticshop.eval.harness;

import com.rmrx.agenticshop.ai.events.ChatResponse;
import com.rmrx.agenticshop.ai.events.RunEventBus;
import com.rmrx.agenticshop.ai.events.RunEventSubscriber;
import com.rmrx.agenticshop.ai.events.RunSubscription;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Waits for a run (started or resumed in the background by {@code RunLauncher}) to reach its next REPLY, INTERACTION
 * or ERROR, through the run's event stream — the same events the chat UI gets. The bus replays buffered events, so
 * subscribing after the send/answer call returned misses nothing; a resume continues the seq, hence {@code afterSeq}.
 * The reply, question or failure is stored before its event is emitted, so reads after the wait see it.
 */
public final class RunAwaiter {

    private final RunEventBus bus;
    private final Duration timeout;

    public RunAwaiter(RunEventBus bus, Duration timeout) {
        this.bus = bus;
        this.timeout = timeout;
    }

    public RunSegment await(UUID runId, long afterSeq) {
        List<ChatResponse> events = new ArrayList<>();
        CompletableFuture<ChatResponse> end = new CompletableFuture<>();
        RunSubscription subscription = bus.subscribe(runId, afterSeq, new RunEventSubscriber() {
            @Override
            public void onEvent(ChatResponse event) {
                synchronized (events) {
                    events.add(event);
                }
                if (event.last()) end.complete(event);
            }

            @Override
            public void onClose() {
                end.completeExceptionally(new IllegalStateException("run " + runId + ": stream closed without a reply"));
            }
        }).orElseThrow(() -> new IllegalStateException("run " + runId + " is not streaming"));
        try {
            ChatResponse last = end.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            synchronized (events) {
                return new RunSegment(runId, List.copyOf(events), last);
            }
        } catch (TimeoutException e) {
            throw new IllegalStateException("run " + runId + " did not finish within " + timeout, e);
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause().getMessage(), e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for run " + runId, e);
        } finally {
            subscription.cancel();
        }
    }
}
