package com.rmrx.agenticshop.ai.graph;

import com.rmrx.agenticshop.ai.context.AgentContext;
import com.rmrx.agenticshop.ai.contract.response.AgentResponse;
import com.rmrx.agenticshop.ai.events.AgentEventPublisher;
import com.rmrx.agenticshop.ai.tools.BoundTools;
import com.rmrx.agenticshop.ai.tools.TurnResultRegistry;
import com.rmrx.agenticshop.ai.usage.RunScope;
import com.rmrx.agenticshop.ai.usage.entity.AiUsagePurpose;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Everything of one turn that is not graph state: the caller's context, the turn's tool registry and bound tools,
 * the event publisher, the deadline, and the outputs of compose and persist. None of it is serializable, so it never
 * enters the LangGraph4j state (which is cloned through a serializer between nodes and will be checkpointed later);
 * nodes reach it through the run's {@code RunnableConfig} metadata. One instance per turn, used by one thread at a
 * time.
 *
 * @param identityLine who the caller is (role and first name, plus the ticket for staff), built on the request thread
 * @param internal whether this turn's reply is a staff-only note (staff copilot turns)
 */
public final class TurnRuntime {

    private final AgentContext ctx;
    private final UUID runId;
    private final Long userMessageId;
    private final String userText;
    private final String identityLine;
    private final boolean internal;
    private final AgentEventPublisher publisher;
    private final TurnResultRegistry registry;
    private final BoundTools tools;
    private final Instant deadline;
    private final String promptVersion;
    private final ActionEvent actionEvent;

    private volatile AgentResponse response;
    private volatile List<Long> actionIds = List.of();
    private volatile Long replyMessageId;
    private volatile boolean finished;
    /** UNAVAILABLE/TIMEOUT tool failures in this turn (auto-escalation trigger). */
    private final java.util.concurrent.atomic.AtomicInteger transientFailures =
            new java.util.concurrent.atomic.AtomicInteger();

    public TurnRuntime(AgentContext ctx, UUID runId, Long userMessageId, String userText, String identityLine,
                       boolean internal, AgentEventPublisher publisher,
                       TurnResultRegistry registry, BoundTools tools, Duration timeout, String promptVersion) {
        this(ctx, runId, userMessageId, userText, identityLine, internal, publisher, registry, tools, timeout,
                promptVersion, null);
    }

    /**
     * A turn started by a chat button: userMessageId is the ACTION message, userText its readable line, and
     * actionEvent what happened (it seeds the graph state).
     */
    public TurnRuntime(AgentContext ctx, UUID runId, Long userMessageId, String userText, String identityLine,
                       boolean internal, AgentEventPublisher publisher, TurnResultRegistry registry,
                       BoundTools tools, Duration timeout, String promptVersion, ActionEvent actionEvent) {
        this.ctx = ctx;
        this.runId = runId;
        this.userMessageId = userMessageId;
        this.userText = userText;
        this.identityLine = identityLine;
        this.internal = internal;
        this.publisher = publisher;
        this.registry = registry;
        this.tools = tools;
        this.deadline = Instant.now().plus(timeout);
        this.promptVersion = promptVersion;
        this.actionEvent = actionEvent;
    }

    /** The button event that started this turn; null for a typed message. */
    public ActionEvent actionEvent() {
        return actionEvent;
    }

    public AgentContext ctx() {
        return ctx;
    }

    public UUID runId() {
        return runId;
    }

    public Long ticketId() {
        return ctx.ticketId();
    }

    public Long userMessageId() {
        return userMessageId;
    }

    public String userText() {
        return userText;
    }

    public String identityLine() {
        return identityLine;
    }

    public boolean internal() {
        return internal;
    }

    public AgentEventPublisher publisher() {
        return publisher;
    }

    public TurnResultRegistry registry() {
        return registry;
    }

    public BoundTools tools() {
        return tools;
    }

    public String promptVersion() {
        return promptVersion;
    }

    public RunScope scope() {
        return RunScope.of(ctx, runId, AiUsagePurpose.CHAT);
    }

    /** Time left before the turn timeout; never negative. */
    public Duration remaining() {
        Duration d = Duration.between(Instant.now(), deadline);
        return d.isNegative() ? Duration.ZERO : d;
    }

    /** Throws {@link TurnTimeoutException} once the turn is past its deadline. */
    public void checkDeadline(String where) {
        if (remaining().isZero()) throw new TurnTimeoutException("turn timed out " + where);
    }

    public AgentResponse response() {
        return response;
    }

    void composed(AgentResponse response, List<Long> actionIds) {
        this.response = response;
        this.actionIds = actionIds == null ? List.of() : List.copyOf(actionIds);
    }

    public List<Long> actionIds() {
        return actionIds;
    }

    public Long replyMessageId() {
        return replyMessageId;
    }

    void persisted(AgentResponse response, Long messageId) {
        this.response = response;
        this.replyMessageId = messageId;
    }

    int recordTransientFailure() {
        return transientFailures.incrementAndGet();
    }

    public int transientFailures() {
        return transientFailures.get();
    }

    /** True once the run was closed (FINAL emitted, run finished). */
    public boolean finished() {
        return finished;
    }

    void markFinished() {
        this.finished = true;
    }
}
