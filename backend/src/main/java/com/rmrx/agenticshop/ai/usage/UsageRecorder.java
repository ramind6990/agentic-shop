package com.rmrx.agenticshop.ai.usage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Writes the token ledger ({@code ai_usage}): one row per successful model call. Each row commits on its own; a
 * failure is logged and never reaches the model call.
 */
@Component("aiUsageRecorder")
public class UsageRecorder {

    private static final Logger log = LoggerFactory.getLogger(UsageRecorder.class);

    private final JdbcTemplate jdbc;

    public UsageRecorder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(UsageScope scope, String provider, String model, TokenCounts tokens) {
        try {
            jdbc.update("""
                            insert into ai_usage (user_id, conversation_id, run_id, issuer, purpose, provider, model,
                                                   input_tokens, cached_input_tokens, output_tokens, reasoning_tokens,
                                                   created_at)
                            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())""",
                    scope.userId(), scope.conversationId(), scope.runId(), scope.issuer().name(),
                    scope.purpose().name(), provider, model, tokens.input(), tokens.cached(), tokens.output(),
                    tokens.reasoning());
        } catch (RuntimeException e) {
            log.error("ai: could not record usage ({} {}, {}) for {}", provider, model, tokens, scope, e);
        }
    }
}
