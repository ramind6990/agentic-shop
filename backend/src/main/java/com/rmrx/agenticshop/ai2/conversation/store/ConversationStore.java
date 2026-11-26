package com.rmrx.agenticshop.ai2.conversation.store;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * The {@code ai2_conversation} table.
 */
@Component
public class ConversationStore {

    private final JdbcTemplate jdbc;
    private final RowMapper<Conversation> mapper = (rs, i) -> new Conversation(
            rs.getLong("id"), rs.getLong("user_id"), Conversation.Status.valueOf(rs.getString("status")));

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

    public void touch(Long id) {
        jdbc.update("update ai2_conversation set updated_at = now() where id = ?", id);
    }

    public void close(Long id) {
        jdbc.update("update ai2_conversation set status = 'CLOSED', updated_at = now() where id = ?", id);
    }
}
