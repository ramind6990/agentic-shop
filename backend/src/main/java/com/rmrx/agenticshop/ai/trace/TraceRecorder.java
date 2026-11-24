package com.rmrx.agenticshop.ai.trace;

import com.rmrx.agenticshop.ai.context.AgentContext;
import com.rmrx.agenticshop.ai.trace.entity.AiRun;
import com.rmrx.agenticshop.ai.trace.entity.AiRunStatus;
import com.rmrx.agenticshop.ai.trace.entity.AiStep;
import com.rmrx.agenticshop.ai.trace.repository.AiRunRepository;
import com.rmrx.agenticshop.ai.trace.repository.AiStepRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

/**
 * Writes the debug trace of a run. Every write commits on its own (REQUIRES_NEW), so the trace survives a failed
 * turn and the run row exists before usage rows reference it. Step seq values come from the run's step counter
 * under a row lock, so concurrent steps of one run never collide. Step and finish failures are logged, not thrown.
 */
@Slf4j
@Service
public class TraceRecorder {

    /** Max characters kept of args and result summaries. */
    public static final int SUMMARY_LIMIT = 2000;
    private static final int NAME_LIMIT = 120;
    private static final int CODE_LIMIT = 60;
    private static final int SHORT_LIMIT = 80;

    private final AiRunRepository runs;
    private final AiStepRepository steps;
    private final TransactionTemplate tx;

    public TraceRecorder(AiRunRepository runs, AiStepRepository steps, PlatformTransactionManager transactionManager) {
        this.runs = runs;
        this.steps = steps;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public UUID startRun(AgentContext ctx, String promptVersion, String model) {
        return startRun(ctx.userId(), ctx.ticketId(), promptVersion, model);
    }

    /** Creates a RUNNING run and returns its id. Throws if the run cannot be saved. */
    public UUID startRun(Long userId, Long ticketId, String promptVersion, String model) {
        UUID id = UUID.randomUUID();
        tx.executeWithoutResult(status -> runs.save(AiRun.start(id, ticketId, userId,
                truncate(promptVersion, 40), truncate(model, SHORT_LIMIT), Instant.now())));
        return id;
    }

    public void recordModelStep(UUID runId, String model, String argsSummary, String resultSummary,
                                String errorCode, Integer latencyMs, Integer inputTokens, Integer outputTokens) {
        writeStep(runId, model, seq -> AiStep.model(runId, seq, truncate(model, NAME_LIMIT),
                truncate(argsSummary, SUMMARY_LIMIT), truncate(resultSummary, SUMMARY_LIMIT),
                truncate(errorCode, CODE_LIMIT), latencyMs, inputTokens, outputTokens, Instant.now()));
    }

    public void recordToolStep(UUID runId, String tool, String argsSummary, String resultSummary,
                               String errorCode, Integer latencyMs) {
        writeStep(runId, tool, seq -> AiStep.tool(runId, seq, truncate(tool, NAME_LIMIT),
                truncate(argsSummary, SUMMARY_LIMIT), truncate(resultSummary, SUMMARY_LIMIT),
                truncate(errorCode, CODE_LIMIT), latencyMs, Instant.now()));
    }

    /** Closes the run with a terminal status. errorCode and message are null on success. */
    public void finishRun(UUID runId, AiRunStatus status, String errorCode, String message) {
        try {
            tx.executeWithoutResult(s -> runs.findForUpdate(runId).ifPresentOrElse(
                    run -> run.finish(status, truncate(errorCode, CODE_LIMIT), message, Instant.now()),
                    () -> log.warn("finishRun: run {} not found", runId)));
        } catch (Exception e) {
            log.error("Failed to finish run {} as {}", runId, status, e);
        }
    }

    private void writeStep(UUID runId, String name, java.util.function.IntFunction<AiStep> factory) {
        try {
            tx.executeWithoutResult(status -> {
                AiRun run = runs.findForUpdate(runId).orElse(null);
                if (run == null) {
                    log.warn("Step {} for unknown run {} dropped", name, runId);
                    return;
                }
                steps.save(factory.apply(run.incrementSteps()));
            });
        } catch (Exception e) {
            log.error("Failed to record step {} of run {}", name, runId, e);
        }
    }

    public static String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s;
        return s.substring(0, max - 1) + "…";
    }
}
