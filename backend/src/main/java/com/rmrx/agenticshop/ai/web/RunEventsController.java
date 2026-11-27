package com.rmrx.agenticshop.ai.web;

import com.rmrx.agenticshop.ai.events.RunEventBus;
import com.rmrx.agenticshop.ai.events.sse.SseRunEventTransport;
import com.rmrx.agenticshop.app.common.exception.BadRequestException;
import com.rmrx.agenticshop.app.common.exception.NotFoundException;
import com.rmrx.agenticshop.app.security.AccessScope;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Objects;
import java.util.UUID;

/**
 * Live events of an ai run as Server-Sent Events; only the run's owner may follow it (anyone else gets 404). Resume
 * with {@code Last-Event-ID} (or {@code ?after=}) set to the last seq seen. A run that is not streamed here (evicted or
 * unknown) yields a single ERROR event with code RUN_NOT_LIVE. Clients must close the connection after FINAL, PAUSED
 * or ERROR (an EventSource would otherwise reconnect); after answering a PAUSED run they subscribe again.
 */
@RestController("aiRunEventsController")
@RequestMapping("/api/ai/runs")
@PreAuthorize("hasAuthority('AI_CHAT')")
public class RunEventsController {

    private final RunEventBus bus;
    private final SseRunEventTransport sse;
    private final AccessScope scope;

    public RunEventsController(RunEventBus bus, SseRunEventTransport sse, AccessScope scope) {
        this.bus = bus;
        this.sse = sse;
        this.scope = scope;
    }

    @GetMapping(value = "/{runId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable UUID runId,
                             @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId,
                             @RequestParam(required = false) Long after) {
        long afterSeq = lastSeen(lastEventId, after);
        return bus.ownerOf(runId)
                .map(owner -> {
                    if (!Objects.equals(owner, scope.current().id())) throw new NotFoundException("Run", runId);
                    return sse.stream(runId, afterSeq);
                })
                .orElseGet(() -> sse.notLive(runId));
    }

    private static long lastSeen(String lastEventId, Long after) {
        if (lastEventId != null && !lastEventId.isBlank()) {
            try {
                return Math.max(0, Long.parseLong(lastEventId.trim()));
            } catch (NumberFormatException e) {
                throw new BadRequestException("Last-Event-ID must be a number");
            }
        }
        return after == null ? 0 : Math.max(0, after);
    }
}
