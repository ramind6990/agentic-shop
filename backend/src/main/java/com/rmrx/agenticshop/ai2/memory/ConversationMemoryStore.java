package com.rmrx.agenticshop.ai2.memory;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * The {@code ai2_conversation_memory} table.
 */
@Component
public class ConversationMemoryStore {

    private final JdbcTemplate jdbc;

    public ConversationMemoryStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ConversationMemory> find(Long conversationId) {
        return jdbc.query("""
                        select conversation_id, summary, summarized_through_message_id
                        from ai2_conversation_memory where conversation_id = ?""",
                (rs, i) -> new ConversationMemory(rs.getLong(1), rs.getString(2), rs.getLong(3)),
                conversationId).stream().findFirst();
    }

    /** The new summary and how far it reaches, in one statement. */
    public void save(Long conversationId, String summary, long summarizedThroughMessageId) {
        jdbc.update("""
                        insert into ai2_conversation_memory (conversation_id, summary, summarized_through_message_id,
                                                             updated_at)
                        values (?, ?, ?, now())
                        on conflict (conversation_id) do update
                        set summary = excluded.summary,
                            summarized_through_message_id = excluded.summarized_through_message_id,
                            updated_at = now()""",
                conversationId, summary, summarizedThroughMessageId);
    }
}
