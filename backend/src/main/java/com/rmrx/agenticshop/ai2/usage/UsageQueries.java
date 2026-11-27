package com.rmrx.agenticshop.ai2.usage;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Reads of the token ledger. */
@Component
public class UsageQueries {

    private static final String SUMS = """
            select issuer, count(*) as calls, coalesce(sum(input_tokens), 0) as input,
                   coalesce(sum(cached_input_tokens), 0) as cached, coalesce(sum(output_tokens), 0) as output,
                   coalesce(sum(reasoning_tokens), 0) as reasoning
            from ai2_usage
            """;

    /** The input size and time of a model call. */
    public record LastCall(int inputTokens, Instant at) {
    }

    private record IssuerRow(UsageIssuer issuer, TokenTotals totals) {
    }

    private static final RowMapper<IssuerRow> ROW = (rs, i) -> new IssuerRow(
            UsageIssuer.valueOf(rs.getString("issuer")),
            TokenTotals.of(rs.getLong("calls"), rs.getLong("input"), rs.getLong("cached"), rs.getLong("output"),
                    rs.getLong("reasoning")));

    private final JdbcTemplate jdbc;

    public UsageQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** A user's totals per issuer in [from, to); both issuers are always present. */
    public Map<UsageIssuer, TokenTotals> byIssuerForUser(Long userId, Instant from, Instant to) {
        return byIssuer(jdbc.query(SUMS + "where user_id = ? and created_at >= ? and created_at < ? group by issuer",
                ROW, userId, Timestamp.from(from), Timestamp.from(to)));
    }

    /** A conversation's totals per issuer over its lifetime; both issuers are always present. */
    public Map<UsageIssuer, TokenTotals> byIssuerForConversation(Long conversationId) {
        return byIssuer(jdbc.query(SUMS + "where conversation_id = ? group by issuer", ROW, conversationId));
    }

    /** The conversation's latest agent (CHAT) call: its input is what the model last had in its context. */
    public Optional<LastCall> lastChatCall(Long conversationId) {
        return jdbc.query("""
                        select input_tokens, created_at from ai2_usage
                        where conversation_id = ? and purpose = 'CHAT'
                        order by id desc limit 1""",
                (rs, i) -> new LastCall(rs.getInt("input_tokens"), rs.getTimestamp("created_at").toInstant()),
                conversationId).stream().findFirst();
    }

    private static Map<UsageIssuer, TokenTotals> byIssuer(List<IssuerRow> rows) {
        Map<UsageIssuer, TokenTotals> totals = new EnumMap<>(UsageIssuer.class);
        for (UsageIssuer issuer : UsageIssuer.values()) totals.put(issuer, TokenTotals.ZERO);
        rows.forEach(r -> totals.put(r.issuer(), r.totals()));
        return totals;
    }
}
