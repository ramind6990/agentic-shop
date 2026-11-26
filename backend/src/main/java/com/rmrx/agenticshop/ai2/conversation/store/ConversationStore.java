package com.rmrx.agenticshop.ai2.conversation.store;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * The {@code ai2_conversation} table.
 */
@Component
public class ConversationStore {

    private static final int TITLE_LENGTH = 60;

    private final JdbcTemplate jdbc;
    private final RowMapper<Conversation> mapper = (rs, i) -> new Conversation(
            rs.getLong("id"), rs.getLong("user_id"), Conversation.Status.valueOf(rs.getString("status")));

    /** The title is the start of the first user message. */
    private static final String SUMMARY = """
            select c.id, c.status, c.updated_at,
                   (select left(m.text, %d) from ai2_conversation_message m
                    where m.conversation_id = c.id and m.kind = 'USER' order by m.id limit 1) as title
            from ai2_conversation c""".formatted(TITLE_LENGTH);
    private final RowMapper<ConversationSummary> summaryMapper = (rs, i) -> new ConversationSummary(
            rs.getLong("id"), rs.getString("title"), Conversation.Status.valueOf(rs.getString("status")),
            rs.getTimestamp("updated_at").toInstant());

    public ConversationStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Conversation create(Long userId) {
        Long id = jdbc.queryForObject("""
                insert into ai2_conversation (user_id, status, created_at, updated_at)
                values (?, 'OPEN', now(), now()) returning id""", Long.class, userId);
        return new Conversation(id, userId, Conversation.Status.OPEN);
    }

    /** The conversation, only when it belongs to {@code userId}. */
    public Optional<Conversation> findOwned(Long id, Long userId) {
        return jdbc.query("select id, user_id, status from ai2_conversation where id = ? and user_id = ?", mapper,
                id, userId).stream().findFirst();
    }

    /** The user's conversations, most recently active first (at most 100). */
    public List<ConversationSummary> listOwned(Long userId) {
        return jdbc.query(SUMMARY + " where c.user_id = ? order by c.updated_at desc, c.id desc limit 100",
                summaryMapper, userId);
    }

    public Optional<ConversationSummary> summaryOwned(Long id, Long userId) {
        return jdbc.query(SUMMARY + " where c.id = ? and c.user_id = ?", summaryMapper, id, userId).stream()
                .findFirst();
    }

    public void touch(Long id) {
        jdbc.update("update ai2_conversation set updated_at = now() where id = ?", id);
    }

    public void close(Long id) {
        jdbc.update("update ai2_conversation set status = 'CLOSED', updated_at = now() where id = ?", id);
    }
}
