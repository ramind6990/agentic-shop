package com.rmrx.agenticshop.ai2.trace;

import com.rmrx.agenticshop.ai2.trace.TraceViews.RunSummary;
import com.rmrx.agenticshop.ai2.trace.TraceViews.RunTrace;
import com.rmrx.agenticshop.ai2.trace.TraceViews.StepView;
import com.rmrx.agenticshop.app.common.exception.NotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Reads of the run traces. */
@Component
public class TraceQueries {

    private static final String RUNS = """
            select r.id, r.conversation_id, r.user_id, r.status, r.prompt_version, r.model, r.started_at,
                   r.finished_at, r.step_count, r.error_code, r.error_message,
                   (select coalesce(sum(u.input_tokens + u.output_tokens), 0) from ai2_usage u where u.run_id = r.id)
                       as total_tokens
            from ai2_run r
            """;

    private static final RowMapper<RunSummary> RUN = (rs, i) -> new RunSummary(
            rs.getObject("id", UUID.class), rs.getLong("conversation_id"), rs.getLong("user_id"),
            RunStatus.valueOf(rs.getString("status")), rs.getString("prompt_version"), rs.getString("model"),
            instant(rs, "started_at"), instant(rs, "finished_at"), rs.getInt("step_count"),
            rs.getLong("total_tokens"), rs.getString("error_code"), rs.getString("error_message"));

    private static final RowMapper<StepView> STEP = (rs, i) -> new StepView(
            rs.getInt("seq"), StepType.valueOf(rs.getString("step_type")), rs.getString("name"),
            integer(rs, "attempt"), rs.getString("args_summary"), rs.getString("result_summary"),
            rs.getString("error_code"), integer(rs, "latency_ms"), integer(rs, "input_tokens"),
            integer(rs, "cached_input_tokens"), integer(rs, "output_tokens"), integer(rs, "reasoning_tokens"),
            instant(rs, "created_at"));

    private final JdbcTemplate jdbc;

    public TraceQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** A run with its steps; 404 when unknown. */
    public RunTrace trace(UUID runId) {
        RunSummary run = jdbc.query(RUNS + "where r.id = ?", RUN, runId).stream().findFirst()
                .orElseThrow(() -> new NotFoundException("Run", runId));
        return new RunTrace(run, jdbc.query("""
                select seq, step_type, name, attempt, args_summary, result_summary, error_code, latency_ms,
                       input_tokens, cached_input_tokens, output_tokens, reasoning_tokens, created_at
                from ai2_run_step where run_id = ? order by seq""", STEP, runId));
    }

    /** A conversation's runs, newest first. */
    public List<RunSummary> runs(Long conversationId) {
        return jdbc.query(RUNS + "where r.conversation_id = ? order by r.started_at desc", RUN, conversationId);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp t = rs.getTimestamp(column);
        return t == null ? null : t.toInstant();
    }

    private static Integer integer(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
