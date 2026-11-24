package com.rmrx.agenticshop.ai.action;

import com.rmrx.agenticshop.ai.action.ActionNotFoundException.Reason;
import com.rmrx.agenticshop.ai.action.entity.ActionTargetType;
import com.rmrx.agenticshop.ai.action.entity.AiAction;
import com.rmrx.agenticshop.ai.action.repository.AiActionRepository;
import com.rmrx.agenticshop.ai.config.AiProperties;
import com.rmrx.agenticshop.ai.context.AgentContext;
import com.rmrx.agenticshop.ai.contract.response.ActionState;
import com.rmrx.agenticshop.ai.contract.response.ActionType;
import com.rmrx.agenticshop.ai.contract.response.ItemAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Issues and resolves action tokens. A token is 32 random bytes from {@link SecureRandom}, base64url without padding
 * (43 chars); it goes to the client once and only its SHA-256 hex is stored in {@code ai_action}, bound to user,
 * ticket, action type, target and params. Whoever holds the token still has to be that user in that ticket.
 *
 * <p>{@link #resolve} accepts a token only if its hash exists, it belongs to the caller and to {@code ctx.ticketId},
 * it is OPEN and not past {@code expires_at} (an overdue OPEN row is marked EXPIRED on the spot). Every failure is the
 * same {@link ActionNotFoundException}; the reason is only logged.
 */
@Slf4j
@Service
public class ActionTokenService {

    private static final int TOKEN_BYTES = 32;
    private static final Pattern TOKEN_SHAPE = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final String INSERT = """
            insert into ai_action (token_hash, ticket_id, user_id, action_type, target_type, target_id, params,
                                   requires_confirmation, state, expires_at, created_at)
            values (?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, 'OPEN', ?, ?)""";

    /** What to issue: one button bound to a target, with its own TTL. */
    public record Spec(ActionType type, String label, ActionTargetType targetType, Long targetId, JsonNode params,
                       boolean requiresConfirmation, Duration ttl) {
    }

    /** An issued action: its stored row and the raw token, which exists only here and in the reply. */
    public record Issued(AiAction action, String token, String label) {

        public ItemAction toItemAction() {
            return new ItemAction(token, action.getActionType(), label, action.isRequiresConfirmation(),
                    action.getState());
        }
    }

    private final AiActionRepository repository;
    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();
    private final Clock clock = Clock.systemUTC();
    private volatile Duration itemTtl;
    private volatile Duration confirmationTtl;

    public ActionTokenService(AiActionRepository repository, JdbcTemplate jdbc, AiProperties props) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.itemTtl = props.actions().itemTtl();
        this.confirmationTtl = props.actions().confirmationTtl();
    }

    public Duration itemTtl() {
        return itemTtl;
    }

    public Duration confirmationTtl() {
        return confirmationTtl;
    }

    /** Dev only: changes the TTLs of actions issued from now on (null keeps the current value). */
    public void overrideTtls(Duration item, Duration confirmation) {
        if (item != null) this.itemTtl = item;
        if (confirmation != null) this.confirmationTtl = confirmation;
        log.warn("Action TTLs changed at runtime: item {}, confirmation {}", itemTtl, confirmationTtl);
    }

    public Instant now() {
        return clock.instant();
    }

    /**
     * Issues all specs for the caller's ticket in one JDBC batch plus one select to read the rows back, so a list of
     * ten orders costs two round trips, not thirty. Returned in spec order.
     */
    public List<Issued> issueAll(AgentContext ctx, List<Spec> specs) {
        if (specs.isEmpty()) return List.of();
        if (ctx.ticketId() == null) throw new IllegalStateException("actions need a ticket in the context");
        Instant now = now();
        List<String> tokens = new ArrayList<>(specs.size());
        List<String> hashes = new ArrayList<>(specs.size());
        List<Object[]> rows = new ArrayList<>(specs.size());
        for (Spec s : specs) {
            String token = newToken();
            String hash = hash(token);
            tokens.add(token);
            hashes.add(hash);
            rows.add(new Object[]{hash, ctx.ticketId(), ctx.userId(), s.type().name(),
                    s.targetType() == null ? null : s.targetType().name(), s.targetId(),
                    s.params() == null ? null : s.params().toString(), s.requiresConfirmation(),
                    Timestamp.from(now.plus(s.ttl())), Timestamp.from(now)});
        }
        jdbc.batchUpdate(INSERT, rows);
        Map<String, AiAction> byHash = repository.findByTokenHashIn(hashes).stream()
                .collect(Collectors.toMap(AiAction::getTokenHash, Function.identity()));
        List<Issued> out = new ArrayList<>(specs.size());
        for (int i = 0; i < specs.size(); i++) {
            out.add(new Issued(byHash.get(hashes.get(i)), tokens.get(i), specs.get(i).label()));
        }
        return out;
    }

    /** The caller's OPEN, unexpired action for this token; otherwise {@link ActionNotFoundException}. */
    public AiAction resolve(String rawToken, AgentContext ctx) {
        if (rawToken == null || !TOKEN_SHAPE.matcher(rawToken).matches()) throw notFound(Reason.MALFORMED, ctx, null);
        if (ctx.ticketId() == null) throw notFound(Reason.NO_TICKET, ctx, null);
        AiAction action = repository.findByTokenHash(hash(rawToken)).orElse(null);
        if (action == null) throw notFound(Reason.UNKNOWN, ctx, null);
        if (!Objects.equals(action.getUserId(), ctx.userId())) throw notFound(Reason.WRONG_USER, ctx, action);
        if (!Objects.equals(action.getTicketId(), ctx.ticketId())) throw notFound(Reason.WRONG_TICKET, ctx, action);
        if (action.getState() != ActionState.OPEN) throw notFound(Reason.NOT_OPEN, ctx, action);
        if (!action.isUsable(now())) {
            action.expire();
            repository.save(action);
            throw notFound(Reason.EXPIRED, ctx, action);
        }
        return action;
    }

    ActionNotFoundException notFound(Reason reason, AgentContext ctx, AiAction action) {
        log.info("Action token rejected: reason={} user={} ticket={} action={}", reason, ctx.userId(), ctx.ticketId(),
                action == null ? null : action.getId());
        return new ActionNotFoundException(reason);
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 hex of the raw token, the only form ever stored. */
    public static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
