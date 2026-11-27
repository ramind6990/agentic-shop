package com.rmrx.agenticshop.eval.harness;

import com.rmrx.agenticshop.app.identity.entity.Role;
import com.rmrx.agenticshop.app.security.CurrentUser;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The seeded logins scenarios act as ({@code customer_1}, {@code support_t1_1} …, from {@code V111__seed_logins}).
 * Their ids differ per database, so they are looked up by username.
 */
public final class EvalActors {

    private final JdbcTemplate jdbc;
    private final Map<String, CurrentUser> cache = new ConcurrentHashMap<>();

    public EvalActors(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public CurrentUser user(String username) {
        return cache.computeIfAbsent(username, this::load);
    }

    public Long id(String username) {
        return user(username).id();
    }

    private CurrentUser load(String username) {
        return jdbc.query("select id, role, seller_id from app_user where username = ?", rs -> {
            if (!rs.next()) throw new IllegalArgumentException("no seeded login " + username + " (V111__seed_logins)");
            long sellerId = rs.getLong("seller_id");
            return new CurrentUser(rs.getLong("id"), username, Role.valueOf(rs.getString("role")),
                    rs.wasNull() ? null : sellerId);
        }, username);
    }
}
