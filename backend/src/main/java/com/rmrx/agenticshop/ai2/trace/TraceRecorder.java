package com.rmrx.agenticshop.ai2.trace;

import com.rmrx.agenticshop.ai2.contract.tool.Texts;
import com.rmrx.agenticshop.ai2.model.Ai2ModelProperties;
import com.rmrx.agenticshop.ai2.model.SystemPrompt;
import com.rmrx.agenticshop.ai2.trace.langfuse.LangfuseExporter;
import com.rmrx.agenticshop.ai2.usage.TokenCounts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Writes run traces ({@code ai2_run}, {@code ai2_run_step}). Every write is one statement that commits on its own, so
 * the trace survives a failed run. A step's seq comes from the run row's counter in the same statement (the row lock
 * orders concurrent steps). Nothing here ever throws: a failed write is logged.
 * <p>
 * Run events and steps also go to {@link LangfuseExporter} (when on), except MODEL steps: {@code ModelGateway} reports
 * those to Langfuse itself, with the full request and response.
 */
@Component("ai2TraceRecorder")
public class TraceRecorder {

    private static final Logger log = LoggerFactory.getLogger(TraceRecorder.class);

    /** Characters kept of args and result summaries. */
    static final int SUMMARY_LIMIT = 2000;
    private static final int NAME_LIMIT = 120;
    private static final int CODE_LIMIT = 60;

    private final JdbcTemplate jdbc;
    private final String promptVersion;
    private final String model;
    private final LangfuseExporter langfuse;

    public TraceRecorder(JdbcTemplate jdbc, SystemPrompt systemPrompt, Ai2ModelProperties modelProps,
                         LangfuseExporter langfuse) {
        this.jdbc = jdbc;
        this.promptVersion = systemPrompt.version();
        this.model = modelProps.modelName();
        this.langfuse = langfuse;
    }

    /** A run starts (new row) or resumes after a pause (RUNNING again). */
    public void runStarted(UUID runId, Long conversationId, Long userId) {
        write("start run " + runId, () -> jdbc.update("""
                        insert into ai2_run (id, conversation_id, user_id, status, prompt_version, model, started_at,
                                             updated_at)
                        values (?, ?, ?, 'RUNNING', ?, ?, now(), now())
                        on conflict (id) do update set status = 'RUNNING', updated_at = now()""",
                runId, conversationId, userId, promptVersion, model));
        langfuse.runStarted(runId, conversationId, userId);
    }

    public void runPaused(UUID runId) {
        write("pause run " + runId, () -> jdbc.update(
                "update ai2_run set status = 'PAUSED', updated_at = now() where id = ?", runId));
        langfuse.runPaused(runId);
    }

    /** Closes the run; errorCode and message are null unless it failed. */
    public void runFinished(UUID runId, RunStatus status, String errorCode, String message) {
        write("finish run " + runId, () -> jdbc.update("""
                        update ai2_run set status = ?, error_code = ?, error_message = ?, finished_at = now(),
                                           updated_at = now()
                        where id = ?""",
                status.name(), Texts.cut(errorCode, CODE_LIMIT), message, runId));
        langfuse.runFinished(runId, status, errorCode, message);
    }

    public void step(UUID runId, TraceStep step) {
        if (runId == null) return;
        TokenCounts tokens = step.tokens();
        write(step.type() + " step of run " + runId, () -> jdbc.update("""
                        with run as (
                            update ai2_run set step_count = step_count + 1, updated_at = now()
                            where id = ?
                            returning id, step_count)
                        insert into ai2_run_step (run_id, seq, step_type, name, attempt, args_summary, result_summary,
                                                  error_code, latency_ms, input_tokens, cached_input_tokens,
                                                  output_tokens, reasoning_tokens, created_at)
                        select id, step_count, ?::varchar, ?::varchar, ?::int, ?::text, ?::text, ?::varchar, ?::int,
                               ?::int, ?::int, ?::int, ?::int, now()
                        from run""",
                runId, step.type().name(), Texts.cut(step.name() == null ? "-" : step.name(), NAME_LIMIT),
                step.attempt(), Texts.cut(step.args(), SUMMARY_LIMIT), Texts.cut(step.result(), SUMMARY_LIMIT),
                Texts.cut(step.errorCode(), CODE_LIMIT),
                step.latencyMs() == null ? null : (int) Math.min(Integer.MAX_VALUE, step.latencyMs()),
                tokens == null ? null : tokens.input(), tokens == null ? null : tokens.cached(),
                tokens == null ? null : tokens.output(), tokens == null ? null : tokens.reasoning()));
        langfuse.step(runId, step);
    }

    private static void write(String what, Runnable statement) {
        try {
            statement.run();
        } catch (RuntimeException e) {
            log.error("ai2 trace: could not {}", what, e);
        }
    }
}
