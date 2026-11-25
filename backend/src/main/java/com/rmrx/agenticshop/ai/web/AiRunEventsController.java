package com.rmrx.agenticshop.ai.web;

import com.rmrx.agenticshop.ai.events.RunEventBus;
import com.rmrx.agenticshop.ai.events.sse.SseRunEventTransport;
import com.rmrx.agenticshop.ai.trace.entity.AiRun;
import com.rmrx.agenticshop.ai.trace.repository.AiRunRepository;
import com.rmrx.agenticshop.app.common.exception.BadRequestException;
import com.rmrx.agenticshop.app.common.exception.NotFoundException;
import com.rmrx.agenticshop.app.security.AccessScope;
import com.rmrx.agenticshop.app.security.CurrentUser;
import com.rmrx.agenticshop.app.security.Permission;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Live events of an agent run as Server-Sent Events. Visible to the run's owner and to holders of AI_TRACE_READ;
 * anyone else gets 404. Resume with {@code Last-Event-ID} (or {@code ?after=}) set to the last seq seen. A run that
 * is known but no longer streamed yields a single ERROR event with code RUN_NOT_LIVE. Clients must close the
 * connection after FINAL or ERROR (an EventSource would otherwise reconnect).
 */
@RestController
@RequestMapping("/api/ai")
public class AiRunEventsController {

    private final RunEventBus bus;
    private final SseRunEventTransport sse;
    private final AiRunRepository runs;
    private final AccessScope scope;

    public AiRunEventsController(RunEventBus bus, SseRunEventTransport sse, AiRunRepository runs,
                                 AccessScope scope) {
        this.bus = bus;
        this.sse = sse;
        this.runs = runs;
        this.scope = scope;
    }

    @GetMapping(value = "/runs/{runId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable UUID runId,
                             @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId,
                             @RequestParam(required = false) Long after) {
        CurrentUser user = scope.current();
        long afterSeq = lastSeen(lastEventId, after);

        Optional<Long> liveOwner = bus.ownerOf(runId);
        if (liveOwner.isPresent()) {
            requireAccess(user, liveOwner.get(), runId);
            return sse.stream(runId, afterSeq);
        }
        AiRun run = runs.findById(runId).orElseThrow(() -> new NotFoundException("Run", runId));
        requireAccess(user, run.getUserId(), runId);
        return sse.notLive(runId);
    }

    private static void requireAccess(CurrentUser user, Long ownerId, UUID runId) {
        if (!Objects.equals(ownerId, user.id()) && !user.has(Permission.AI_TRACE_READ)) {
            throw new NotFoundException("Run", runId);
        }
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
