package com.rmrx.agenticshop.ai.conversation;

import com.rmrx.agenticshop.ai.config.AiProperties;
import com.rmrx.agenticshop.ai.context.AgentContext;
import com.rmrx.agenticshop.ai.context.AgentContextFactory;
import com.rmrx.agenticshop.ai.events.AgentEventPublisher;
import com.rmrx.agenticshop.ai.events.RunEvents;
import com.rmrx.agenticshop.ai.graph.JdbcCheckpointSaver;
import com.rmrx.agenticshop.ai.graph.ReplyWriter;
import com.rmrx.agenticshop.ai.graph.SupportAgentGraph;
import com.rmrx.agenticshop.ai.graph.TurnRuntime;
import com.rmrx.agenticshop.ai.tools.BoundTools;
import com.rmrx.agenticshop.ai.tools.ToolCatalog;
import com.rmrx.agenticshop.ai.tools.TurnResultRegistry;
import com.rmrx.agenticshop.ai.trace.TraceRecorder;
import com.rmrx.agenticshop.ai.trace.entity.AiRun;
import com.rmrx.agenticshop.ai.trace.entity.AiRunStatus;
import com.rmrx.agenticshop.ai.trace.repository.AiRunRepository;
import com.rmrx.agenticshop.app.identity.entity.AppUser;
import com.rmrx.agenticshop.app.identity.repository.AppUserRepository;
import com.rmrx.agenticshop.app.security.CurrentUser;
import com.rmrx.agenticshop.app.support.entity.TicketStatus;
import com.rmrx.agenticshop.app.support.repository.TicketRepository;
import dev.langchain4j.model.chat.StreamingChatModel;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repairs runs left RUNNING by a restart (the app was killed mid-turn). Runs once when the app is ready; at that
 * point no turn of this process exists yet, so every RUNNING run is an orphan (single instance, like
 * {@link ActiveRuns}). The in-memory leases and event buffers died with the old process; a resumed run gets a new
 * lease and a new event buffer under its run id (a client may resubscribe to it).
 *
 * <p>Per run, in order:
 * <ol>
 * <li>Its ENVELOPE exists (killed between saving the reply and closing the run): the run is closed COMPLETED.
 * Nothing is written again, so there is never a second reply.</li>
 * <li>Its newest checkpoint passes {@link SupportAgentGraph#resumeBlocker} (before any tool result: next node
 * {@code agent} or {@code tools}, a typed turn), it is younger than {@code resumeMaxAge}, AI is enabled and the
 * ticket still takes the turn (not CLOSED; not ESCALATED for a customer): the run is resumed from that checkpoint.
 * Rebuilt for it: the {@link AgentContext} from {@code ai_run.user_id} + {@code ticket_id} (user's current role),
 * a fresh TurnResultRegistry and BoundTools (exact, since no tool had returned), a new publisher, a new deadline.
 * The model input itself (system prompt, summary, history, user message) is in the checkpointed state.</li>
 * <li>Otherwise: an ERROR envelope is saved ("interrupted, please try again"), the run is closed FAILED with code
 * {@code INTERRUPTED} and the reason. The user's message was saved before the run, so nothing is lost; the ticket
 * takes new messages right away.</li>
 * </ol>
 * Writes cannot be duplicated by either path: the graph never commits a write (writes run only in the action
 * endpoint, on a confirmation click, before a turn starts); a re-run tool call can at most re-issue an OPEN proposal,
 * and its card is built from the new one. Checkpoints of every handled run are deleted; each decision is traced as a
 * {@code recovery} step.
 */
@Slf4j
@Component
public class RunRecovery {

    public static final String INTERRUPTED = "INTERRUPTED";
    public static final String INTERRUPTED_TEXT = "Sorry, I was interrupted while working on this (our service "
            + "restarted). Please send your message again.";

    private final AiRunRepository runs;
    private final JdbcCheckpointSaver checkpoints;
    private final ReplyWriter replies;
    private final TraceRecorder trace;
    private final ActiveRuns activeRuns;
    private final RunEvents runEvents;
    private final ToolCatalog toolCatalog;
    private final AppUserRepository users;
    private final TicketRepository tickets;
    private final AgentContextFactory contexts;
    private final TurnRunner runner;
    private final AiProperties props;
    private final ObjectProvider<StreamingChatModel> streamingModel;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate readTx;

    public RunRecovery(AiRunRepository runs, JdbcCheckpointSaver checkpoints, ReplyWriter replies,
                       TraceRecorder trace, ActiveRuns activeRuns, RunEvents runEvents, ToolCatalog toolCatalog,
                       AppUserRepository users, TicketRepository tickets, AgentContextFactory contexts,
                       TurnRunner runner, AiProperties props, ObjectProvider<StreamingChatModel> streamingModel,
                       JdbcTemplate jdbc, PlatformTransactionManager tm) {
        this.runs = runs;
        this.checkpoints = checkpoints;
        this.replies = replies;
        this.trace = trace;
        this.activeRuns = activeRuns;
        this.runEvents = runEvents;
        this.toolCatalog = toolCatalog;
        this.users = users;
        this.tickets = tickets;
        this.contexts = contexts;
        this.runner = runner;
        this.props = props;
        this.streamingModel = streamingModel;
        this.jdbc = jdbc;
        this.readTx = new TransactionTemplate(tm);
        this.readTx.setReadOnly(true);
    }

    public record Outcome(int completed, int resumed, int failed) {
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        try {
            Outcome o = recover();
            int stale = checkpoints.deleteOfFinishedRuns();
            if (o.completed() + o.resumed() + o.failed() + stale > 0) {
                log.warn("Run recovery after restart: {} closed as completed, {} resumed, {} failed; {} stale "
                        + "checkpoints deleted", o.completed(), o.resumed(), o.failed(), stale);
            }
        } catch (RuntimeException e) {
            log.error("Run recovery failed", e);
        }
    }

    /** Handles every RUNNING run; see the class comment. */
    public Outcome recover() {
        int completed = 0, resumed = 0, failed = 0;
        List<AiRun> orphans = runs.findByStatusOrderByStartedAtAsc(AiRunStatus.RUNNING);
        for (AiRun run : orphans) {
            try {
                switch (handle(run)) {
                    case COMPLETED -> completed++;
                    case RESUMED -> resumed++;
                    case FAILED -> failed++;
                }
            } catch (RuntimeException e) {
                log.error("Recovery of run {} failed", run.getId(), e);
                failed++;
                trace.finishRun(run.getId(), AiRunStatus.FAILED, INTERRUPTED, "recovery failed: " + e.getMessage());
                checkpoints.deleteRun(run.getId());
            }
        }
        return new Outcome(completed, resumed, failed);
    }

    private enum Decision { COMPLETED, RESUMED, FAILED }

    private Decision handle(AiRun run) {
        if (run.getTicketId() == null) {
            trace.finishRun(run.getId(), AiRunStatus.FAILED, INTERRUPTED, "interrupted by a restart (no ticket)");
            checkpoints.deleteRun(run.getId());
            return Decision.FAILED;
        }
        if (envelopeExists(run)) {
            trace.recordToolStep(run.getId(), "recovery", "after restart", "reply was already saved; closed "
                    + "COMPLETED without writing again", null, 0);
            trace.finishRun(run.getId(), AiRunStatus.COMPLETED, null, null);
            checkpoints.deleteRun(run.getId());
            log.warn("Run {} (ticket {}): reply already saved before the restart; closed COMPLETED", run.getId(),
                    run.getTicketId());
            return Decision.COMPLETED;
        }
        AgentContext ctx = context(run);
        Optional<Checkpoint> last = loadLatest(run);
        String blocker = blocker(run, ctx, last.orElse(null));
        if (blocker == null) {
            Optional<ActiveRuns.Lease> lease = activeRuns.tryAcquire(run.getTicketId());
            if (lease.isPresent()) {
                resume(run, ctx, last.get(), lease.get());
                return Decision.RESUMED;
            }
            blocker = "the ticket already has an active run";
        }
        fail(run, ctx, blocker);
        return Decision.FAILED;
    }

    private Optional<Checkpoint> loadLatest(AiRun run) {
        try {
            return checkpoints.latest(run.getId());
        } catch (RuntimeException e) {
            log.warn("Run {}: newest checkpoint unreadable: {}", run.getId(), e.toString());
            return Optional.empty();
        }
    }

    /** Why the run is not resumed, or null. */
    private String blocker(AiRun run, AgentContext ctx, Checkpoint last) {
        if (!props.agent().checkpoints()) return "checkpoints are disabled";
        if (streamingModel.getIfAvailable() == null) return "AI is disabled";
        Duration age = Duration.between(run.getStartedAt(), Instant.now());
        if (age.compareTo(props.agent().resumeMaxAge()) > 0) return "too old to resume (" + age.toSeconds() + "s)";
        if (ctx == null) return "user not found";
        if (ctx.isSeller()) return "sellers cannot use the assistant";
        TicketStatus status = readTx.execute(s -> tickets.findById(run.getTicketId()).map(t -> t.getStatus())
                .orElse(null));
        if (status == null) return "ticket not found";
        if (status == TicketStatus.CLOSED) return "ticket is closed";
        if (status == TicketStatus.ESCALATED && ctx.isCustomer()) return "ticket was escalated";
        return SupportAgentGraph.resumeBlocker(last);
    }

    private void resume(AiRun run, AgentContext ctx, Checkpoint from, ActiveRuns.Lease lease) {
        try {
            activeRuns.bind(lease, run.getId());
            AgentEventPublisher publisher = runEvents.open(run.getId(), ctx.userId());
            publisher.runStarted(run.getTicketId());
            TurnResultRegistry registry = new TurnResultRegistry();
            BoundTools tools = toolCatalog.toolsFor(ctx, registry);
            // userMessageId, userText and identityLine only feed loadContext, which is not re-run: the model input
            // is in the checkpointed state
            TurnRuntime rt = new TurnRuntime(ctx, run.getId(), null, "", "", ctx.isStaff(), publisher, registry,
                    tools, props.agent().turnTimeout(), run.getPromptVersion());
            trace.recordToolStep(run.getId(), "recovery", "after restart, from checkpoint " + from.getId()
                            + " (after " + from.getNodeId() + ")",
                    "resumed at " + from.getNextNodeId() + "; context rebuilt for user " + ctx.userId() + " ("
                            + ctx.role() + "), fresh tool registry", null, 0);
            log.warn("Run {} (ticket {}) resumes after restart at {} (checkpoint {})", run.getId(),
                    run.getTicketId(), from.getNextNodeId(), from.getId());
            runner.submitResume(rt, lease);
        } catch (RuntimeException e) {
            activeRuns.release(lease);
            throw e;
        }
    }

    private void fail(AiRun run, AgentContext ctx, String reason) {
        boolean internal = ctx != null && ctx.isStaff();
        try {
            replies.save(run.getTicketId(), ReplyWriter.errorResponse(run.getId(), INTERRUPTED_TEXT), internal);
        } catch (RuntimeException e) {
            log.error("Could not save the interruption reply of run {}", run.getId(), e);
        }
        trace.recordToolStep(run.getId(), "recovery", "after restart", "not resumable: " + reason
                + "; ERROR reply saved", INTERRUPTED, 0);
        trace.finishRun(run.getId(), AiRunStatus.FAILED, INTERRUPTED,
                "interrupted by a restart; not resumed: " + reason);
        checkpoints.deleteRun(run.getId());
        log.warn("Run {} (ticket {}) interrupted by a restart and failed: {}", run.getId(), run.getTicketId(),
                reason);
    }

    private boolean envelopeExists(AiRun run) {
        Boolean exists = jdbc.queryForObject("select exists (select 1 from ticket_message where ticket_id = ? "
                        + "and kind = 'ENVELOPE' and payload ->> 'runId' = ?)", Boolean.class, run.getTicketId(),
                run.getId().toString());
        return Boolean.TRUE.equals(exists);
    }

    /** The run's caller as of now (current role), bound to the run's ticket; null if the user is gone. */
    private AgentContext context(AiRun run) {
        return readTx.execute(s -> users.findById(run.getUserId()).map(u -> contexts.from(currentUser(u),
                run.getTicketId())).orElse(null));
    }

    private static CurrentUser currentUser(AppUser u) {
        return new CurrentUser(u.getId(), u.getUsername(), u.getRole(),
                u.getSeller() == null ? null : u.getSeller().getId());
    }
}
