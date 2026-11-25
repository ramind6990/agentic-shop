package com.rmrx.agenticshop.ai.conversation;

import com.rmrx.agenticshop.ai.graph.JdbcCheckpointSaver;
import com.rmrx.agenticshop.ai.graph.ReplyWriter;
import com.rmrx.agenticshop.ai.memory.ConversationSummarizer;
import com.rmrx.agenticshop.ai.memory.SummaryAudience;
import com.rmrx.agenticshop.ai.graph.SupportAgentGraph;
import com.rmrx.agenticshop.ai.graph.TurnRuntime;
import com.rmrx.agenticshop.ai.graph.TurnTimeoutException;
import com.rmrx.agenticshop.ai.trace.TraceRecorder;
import com.rmrx.agenticshop.ai.trace.entity.AiRunStatus;
import com.rmrx.agenticshop.ai.usage.RunScopes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;

/**
 * Runs a turn's graph on {@code aiExecutor} (one virtual thread per turn) with the turn's RunScope bound inside the
 * task, since a ScopedValue is not inherited by executor tasks. Whatever happens, the ticket's run lease is released.
 *
 * <p>On failure (exception or turn timeout) before the reply was persisted: an ERROR envelope is saved so the
 * timeline stays coherent, the run is finished FAILED and the stream gets an ERROR event (retryable). The user's
 * message was saved before the run started, so it is never lost.
 *
 * <p>When the run ends (either way) its checkpoints are deleted; after a successful turn the ticket's rolling summary
 * is updated if due ({@link ConversationSummarizer}, never failing the turn).
 */
@Slf4j
@Component
public class TurnRunner {

    public static final String TIMEOUT = "TIMEOUT";
    public static final String AGENT_ERROR = "AGENT_ERROR";

    private final SupportAgentGraph graph;
    private final ReplyWriter replies;
    private final TraceRecorder trace;
    private final ActiveRuns activeRuns;
    private final ExecutorService executor;
    private final JdbcCheckpointSaver checkpoints;
    private final ConversationSummarizer summarizer;

    public TurnRunner(SupportAgentGraph graph, ReplyWriter replies, TraceRecorder trace, ActiveRuns activeRuns,
                      @Qualifier("aiExecutor") ExecutorService executor, JdbcCheckpointSaver checkpoints,
                      ConversationSummarizer summarizer) {
        this.checkpoints = checkpoints;
        this.summarizer = summarizer;
        this.graph = graph;
        this.replies = replies;
        this.trace = trace;
        this.activeRuns = activeRuns;
        this.executor = executor;
    }

    public void submit(TurnRuntime rt, ActiveRuns.Lease lease) {
        submit(rt, lease, false);
    }

    /** Continues a run interrupted by a restart from its newest checkpoint (see {@code RunRecovery}). */
    public void submitResume(TurnRuntime rt, ActiveRuns.Lease lease) {
        submit(rt, lease, true);
    }

    /**
     * The turn, then retention and the lease, then (successful turns only) the rolling summary. The summary runs
     * after the lease is released, so the user can send the next message right after FINAL; it bills to the same
     * run scope (purpose SUMMARY) and a concurrent fold of the same summary loses on its optimistic version.
     */
    private void submit(TurnRuntime rt, ActiveRuns.Lease lease, boolean resume) {
        executor.execute(() -> {
            boolean ok = false;
            try {
                ok = RunScopes.get(rt.scope(), () -> runTurn(rt, resume));
            } finally {
                // retention: a run that ended in this process is never resumed, so its checkpoints go now
                checkpoints.deleteRun(rt.runId());
                activeRuns.release(lease);
            }
            if (ok) summarizer.afterTurn(rt.scope(), rt.ticketId(), SummaryAudience.of(rt.ctx()));
        });
    }

    private boolean runTurn(TurnRuntime rt, boolean resume) {
        long t0 = System.currentTimeMillis();
        try {
            if (resume) graph.resume(rt);
            else graph.run(rt);
            log.info("Run {} (ticket {}) {} in {} ms", rt.runId(), rt.ticketId(),
                    resume ? "resumed and completed" : "completed", System.currentTimeMillis() - t0);
            return true;
        } catch (Throwable e) {
            fail(rt, e);
            return false;
        }
    }

    private void fail(TurnRuntime rt, Throwable e) {
        boolean timeout = causedBy(e, TurnTimeoutException.class);
        String code = timeout ? TIMEOUT : AGENT_ERROR;
        String detail = rootMessage(e);
        if (rt.finished()) {
            // FINAL went out and the run is closed; nothing left to repair
            log.error("Run {} failed after completing: {}", rt.runId(), detail, e);
            return;
        }
        log.error("Run {} (ticket {}) failed: {} {}", rt.runId(), rt.ticketId(), code, detail, e);
        if (rt.replyMessageId() == null) {
            try {
                replies.save(rt.ticketId(), ReplyWriter.errorResponse(rt.runId(), timeout
                        ? "Sorry, this took too long and I had to stop. Please try again." : null), rt.internal());
            } catch (RuntimeException saveError) {
                log.error("Could not persist the error reply of run {}", rt.runId(), saveError);
            }
        }
        trace.finishRun(rt.runId(), AiRunStatus.FAILED, code, TraceRecorder.truncate(detail, 1000));
        rt.publisher().fail(code, timeout ? "The assistant took too long to answer."
                : "The assistant could not complete this reply.", true);
    }

    private static boolean causedBy(Throwable e, Class<? extends Throwable> type) {
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (type.isInstance(t)) return true;
        }
        return false;
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        return t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
    }
}
