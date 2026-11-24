package com.rmrx.agenticshop.ai.web.dev;

import com.rmrx.agenticshop.ai.contract.block.OrderListBlock;
import com.rmrx.agenticshop.ai.contract.block.OrderSummaryItem;
import com.rmrx.agenticshop.ai.contract.response.AgentResponse;
import com.rmrx.agenticshop.ai.contract.response.ResponseStatus;
import com.rmrx.agenticshop.ai.events.AgentEventPublisher;
import com.rmrx.agenticshop.ai.events.RunEvents;
import com.rmrx.agenticshop.ai.trace.TraceRecorder;
import com.rmrx.agenticshop.ai.trace.entity.AiRunStatus;
import com.rmrx.agenticshop.order.entity.OrderStatus;
import com.rmrx.agenticshop.security.AccessScope;
import com.rmrx.agenticshop.security.CurrentUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * Dev-only fake agent run that emits a realistic event sequence without any model, for exercising the event
 * transports. Present only with {@code app.ai.dev-endpoints=true}.
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/dev")
@ConditionalOnBooleanProperty("app.ai.dev-endpoints")
public class AiDevFakeRunController {

    private static final String DEFAULT_TEXT =
            "You have two recent orders. The latest one shipped yesterday and should arrive by Friday.";
    private static final String TOOL = "get_my_orders";

    private final TraceRecorder trace;
    private final RunEvents runEvents;
    private final ExecutorService aiExecutor;
    private final AccessScope scope;

    public AiDevFakeRunController(TraceRecorder trace, RunEvents runEvents,
                                  @Qualifier("aiExecutor") ExecutorService aiExecutor, AccessScope scope) {
        this.trace = trace;
        this.runEvents = runEvents;
        this.aiExecutor = aiExecutor;
        this.scope = scope;
    }

    /** tokens: the reply text (streamed word by word); delayMs: pause between events (0..2000); fail: end in ERROR. */
    public record FakeRunRequest(String tokens, Integer delayMs, Boolean fail) {
    }

    public record FakeRunResponse(UUID runId) {
    }

    /** Starts a fake run owned by the caller and returns its id at once; events follow on the AI executor. */
    @PostMapping("/fake-run")
    public FakeRunResponse fakeRun(@RequestBody(required = false) FakeRunRequest request) {
        String text = request == null || request.tokens() == null || request.tokens().isBlank()
                ? DEFAULT_TEXT : request.tokens();
        long delay = request == null || request.delayMs() == null ? 150 : Math.clamp(request.delayMs(), 0, 2000);
        boolean fail = request != null && Boolean.TRUE.equals(request.fail());

        CurrentUser user = scope.current();
        UUID runId = trace.startRun(user.id(), null, "dev-fake-run", "fake");
        AgentEventPublisher events = runEvents.open(runId, user.id());
        aiExecutor.execute(() -> simulate(events, text, Duration.ofMillis(delay), fail));
        return new FakeRunResponse(runId);
    }

    private void simulate(AgentEventPublisher events, String text, Duration delay, boolean fail) {
        UUID runId = events.runId();
        try {
            events.runStarted(null);
            pause(delay);
            events.toolStarted(TOOL, "Looking up your orders…");
            long started = System.nanoTime();
            pause(delay.multipliedBy(2));
            events.toolFinished(TOOL, true, null, Duration.ofNanos(System.nanoTime() - started).toMillis());
            OrderListBlock block = fakeOrders(runId);
            events.blockReady(block);
            pause(delay);
            String[] words = text.split("(?<=\\s)");
            for (String word : words) {
                events.tokenDelta(word);
                pause(delay);
            }
            if (fail) {
                events.fail("FAKE_FAILURE", "Simulated failure requested by the caller.", true);
                trace.finishRun(runId, AiRunStatus.FAILED, "FAKE_FAILURE", "Simulated failure");
                return;
            }
            events.complete(new AgentResponse(AgentResponse.CURRENT_SCHEMA_VERSION, ResponseStatus.ANSWERED, text,
                    List.of(block), List.of(), List.of(), runId, null, Instant.now()));
            trace.finishRun(runId, AiRunStatus.COMPLETED, null, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            events.fail("CANCELLED", "The run was interrupted.", true);
            trace.finishRun(runId, AiRunStatus.CANCELLED, "INTERRUPTED", null);
        } catch (RuntimeException e) {
            log.error("Fake run {} failed", runId, e);
            events.fail("INTERNAL", "Something went wrong.", true);
            trace.finishRun(runId, AiRunStatus.FAILED, e.getClass().getSimpleName(), e.getMessage());
        }
    }

    private static OrderListBlock fakeOrders(UUID runId) {
        Instant now = Instant.now();
        List<OrderSummaryItem> items = List.of(
                new OrderSummaryItem(900001L, "AS-900001", OrderStatus.SHIPPED, now.minus(Duration.ofDays(2)),
                        new BigDecimal("59.90"), "EUR", 2, "Wireless earbuds", List.of()),
                new OrderSummaryItem(900002L, "AS-900002", OrderStatus.DELIVERED, now.minus(Duration.ofDays(12)),
                        new BigDecimal("24.50"), "EUR", 1, "Phone case", List.of()));
        return new OrderListBlock("fake-" + runId.toString().substring(0, 8), items, 2, false);
    }

    private static void pause(Duration d) throws InterruptedException {
        if (!d.isZero()) Thread.sleep(d);
    }
}
