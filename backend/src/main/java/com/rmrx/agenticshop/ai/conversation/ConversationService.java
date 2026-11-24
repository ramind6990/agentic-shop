package com.rmrx.agenticshop.ai.conversation;

import com.rmrx.agenticshop.ai.action.ActionService;
import com.rmrx.agenticshop.ai.action.ActionTokenService;
import com.rmrx.agenticshop.ai.action.entity.AiAction;
import com.rmrx.agenticshop.ai.action.repository.AiActionRepository;
import com.rmrx.agenticshop.ai.config.AiProperties;
import com.rmrx.agenticshop.ai.context.AgentContext;
import com.rmrx.agenticshop.ai.contract.request.StartConversationRequest;
import com.rmrx.agenticshop.ai.conversation.ConversationDtos.ConversationClosed;
import com.rmrx.agenticshop.ai.conversation.ConversationDtos.TimelineMessage;
import com.rmrx.agenticshop.ai.conversation.ConversationDtos.TurnAccepted;
import com.rmrx.agenticshop.ai.events.AgentEventPublisher;
import com.rmrx.agenticshop.ai.events.RunEvents;
import com.rmrx.agenticshop.ai.graph.PromptRegistry;
import com.rmrx.agenticshop.ai.graph.TurnRuntime;
import com.rmrx.agenticshop.ai.tools.BoundTools;
import com.rmrx.agenticshop.ai.tools.ToolCatalog;
import com.rmrx.agenticshop.ai.tools.TurnResultRegistry;
import com.rmrx.agenticshop.ai.trace.TraceRecorder;
import com.rmrx.agenticshop.ai.trace.entity.AiRunStatus;
import com.rmrx.agenticshop.common.dto.PageResponse;
import com.rmrx.agenticshop.common.exception.NotFoundException;
import com.rmrx.agenticshop.identity.entity.AppUser;
import com.rmrx.agenticshop.identity.repository.AppUserRepository;
import com.rmrx.agenticshop.order.entity.CustomerOrder;
import com.rmrx.agenticshop.order.repository.CustomerOrderRepository;
import com.rmrx.agenticshop.support.entity.AuthorType;
import com.rmrx.agenticshop.support.entity.MessageKind;
import com.rmrx.agenticshop.support.entity.SupportTier;
import com.rmrx.agenticshop.support.entity.Ticket;
import com.rmrx.agenticshop.support.entity.TicketCategory;
import com.rmrx.agenticshop.support.entity.TicketMessage;
import com.rmrx.agenticshop.support.entity.TicketPriority;
import com.rmrx.agenticshop.support.entity.TicketStatus;
import com.rmrx.agenticshop.support.repository.TicketMessageRepository;
import com.rmrx.agenticshop.support.repository.TicketRepository;
import com.rmrx.agenticshop.support.service.TicketService;
import dev.langchain4j.model.chat.StreamingChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Support conversations on top of tickets. A turn is accepted synchronously (user message saved, run started, event
 * stream opened) and then runs on {@code aiExecutor}; the client follows it on {@code /api/ai/runs/{runId}/events}.
 *
 * <p>Who may chat: customers in their own tickets; support staff and admins in any ticket they can see, where their
 * turns (question and reply) are internal staff notes, so a staff copilot answer never reaches the customer. Sellers
 * are excluded. At most one active run per ticket (409 otherwise). Everything needs {@code app.ai.enabled=true}
 * (503 otherwise).
 *
 * <p>Status handling: {@link #close} marks the ticket RESOLVED, not CLOSED. RESOLVED means "handled, may come back":
 * a new message reopens it (OPEN), which is what a customer ending a chat expects. CLOSED is the terminal back-office
 * state; a closed ticket rejects new messages (409).
 */
@Slf4j
@Service
public class ConversationService {

    public static final int SUBJECT_MAX = 120;
    public static final int MAX_PAGE_SIZE = 100;

    private final TicketService ticketAccess;
    private final TicketRepository tickets;
    private final TicketMessageRepository messages;
    private final AppUserRepository users;
    private final CustomerOrderRepository orders;
    private final ActiveRuns activeRuns;
    private final TraceRecorder trace;
    private final RunEvents runEvents;
    private final ToolCatalog toolCatalog;
    private final TurnRunner runner;
    private final PromptRegistry prompts;
    private final AiProperties props;
    private final ObjectProvider<StreamingChatModel> streamingModel;
    private final AiActionRepository actionRepository;
    private final ActionService actionService;
    private final TransactionTemplate tx;
    private final TransactionTemplate readTx;

    public ConversationService(TicketService ticketAccess, TicketRepository tickets, TicketMessageRepository messages,
                               AppUserRepository users, CustomerOrderRepository orders, ActiveRuns activeRuns,
                               TraceRecorder trace, RunEvents runEvents, ToolCatalog toolCatalog, TurnRunner runner,
                               PromptRegistry prompts, AiProperties props,
                               ObjectProvider<StreamingChatModel> streamingModel,
                               AiActionRepository actionRepository, ActionService actionService,
                               PlatformTransactionManager tm) {
        this.ticketAccess = ticketAccess;
        this.tickets = tickets;
        this.messages = messages;
        this.users = users;
        this.orders = orders;
        this.activeRuns = activeRuns;
        this.trace = trace;
        this.runEvents = runEvents;
        this.toolCatalog = toolCatalog;
        this.runner = runner;
        this.prompts = prompts;
        this.props = props;
        this.streamingModel = streamingModel;
        this.actionRepository = actionRepository;
        this.actionService = actionService;
        this.tx = new TransactionTemplate(tm);
        this.readTx = new TransactionTemplate(tm);
        this.readTx.setReadOnly(true);
    }

    // ------------------------------------------------------------------ start

    /** Opens a ticket for the calling customer and sends the first message. */
    public TurnAccepted start(AgentContext ctx, StartConversationRequest req) {
        requireAi();
        requireChatRole(ctx);
        if (!ctx.isCustomer()) {
            throw new ConversationExceptions.NotAllowed("Only customers start conversations; staff join an "
                    + "existing ticket with POST /api/ai/conversations/{ticketId}/messages");
        }
        Long ticketId = tx.execute(status -> {
            AppUser customer = users.getReferenceById(ctx.userId());
            CustomerOrder order = null;
            if (req.orderId() != null) {
                order = orders.findById(req.orderId())
                        .filter(o -> Objects.equals(o.getCustomer().getId(), ctx.userId()))
                        .orElseThrow(() -> new NotFoundException("Order", req.orderId()));
            }
            Instant now = Instant.now();
            String temp = "TCK-T" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
            Ticket t = tickets.saveAndFlush(Ticket.open(temp, customer, order,
                    order == null ? null : order.getSeller(), subjectOf(req), categoryOf(req.text(), order != null),
                    TicketPriority.NORMAL, SupportTier.T1, now));
            t.assignNumber(ticketNumber(t.getId()));
            return t.getId();
        });
        log.info("Conversation ticket {} opened by user {}", ticketId, ctx.userId());
        return send(ctx.withTicketId(ticketId), ticketId, req.text());
    }

    public static String ticketNumber(Long id) {
        return "TCK-" + String.format("%07d", id);
    }

    private static String subjectOf(StartConversationRequest req) {
        String s = req.subject() != null && !req.subject().isBlank() ? req.subject() : req.text();
        s = s.strip().lines().findFirst().orElse("").replaceAll("\\s+", " ");
        if (s.isEmpty()) s = "Support request";
        return s.length() <= SUBJECT_MAX ? s : s.substring(0, SUBJECT_MAX - 1).stripTrailing() + "…";
    }

    /** A simple keyword guess; ORDER when nothing matches (most chats are about an order). */
    static TicketCategory categoryOf(String text, boolean hasOrder) {
        String t = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (containsAny(t, "return", "refund", "exchange", "broken", "damaged", "defect")) return TicketCategory.RETURN;
        if (containsAny(t, "ship", "track", "deliver", "parcel", "package", "courier")) return TicketCategory.SHIPPING;
        if (containsAny(t, "payment", "charge", "card", "invoice", "billing", "paid")) return TicketCategory.PAYMENT;
        if (!hasOrder && containsAny(t, "account", "password", "login", "log in", "email", "address"))
            return TicketCategory.ACCOUNT;
        if (!hasOrder && containsAny(t, "product", "size", "stock", "available", "price", "recommend"))
            return TicketCategory.PRODUCT;
        return TicketCategory.ORDER;
    }

    private static boolean containsAny(String text, String... words) {
        for (String w : words) if (text.contains(w)) return true;
        return false;
    }

    // ------------------------------------------------------------------ send

    /** Saves the user's message and starts a run; returns at once (the reply streams). */
    public TurnAccepted send(AgentContext ctx, Long ticketId, String text) {
        requireAi();
        requireChatRole(ctx);
        ticketAccess.get(ticketId); // 404 unless the caller may see the ticket
        AgentContext tctx = ctx.withTicketId(ticketId);
        TicketStatus current = readTx.execute(s -> ticket(ticketId).getStatus());
        if (current == TicketStatus.CLOSED) throw new ConversationExceptions.Conflict("Ticket is closed");

        ActiveRuns.Lease lease = activeRuns.tryAcquire(ticketId).orElseThrow(() ->
                new ConversationExceptions.Conflict("A reply is still being written in this ticket; "
                        + "wait for it to finish"));
        UUID runId = null;
        try {
            boolean internal = tctx.isStaff();
            Saved saved = tx.execute(s -> saveUserMessage(tctx, ticketId, text, internal));
            PromptRegistry.Prompt prompt = prompts.supportAgent();
            runId = trace.startRun(tctx, prompt.id(), props.chat().model());
            activeRuns.bind(lease, runId);
            AgentEventPublisher publisher = runEvents.open(runId, tctx.userId());
            publisher.runStarted(ticketId);
            TurnResultRegistry registry = new TurnResultRegistry();
            BoundTools tools = toolCatalog.toolsFor(tctx, registry);
            TurnRuntime rt = new TurnRuntime(tctx, runId, saved.messageId(), text, saved.identityLine(), internal,
                    publisher, registry, tools, props.agent().turnTimeout(), prompt.id());
            runner.submit(rt, lease);
            return new TurnAccepted(runId, ticketId, saved.ticketNumber(), saved.messageId());
        } catch (RuntimeException e) {
            activeRuns.release(lease);
            if (runId != null) trace.finishRun(runId, AiRunStatus.FAILED, "START_FAILED", e.getMessage());
            throw e;
        }
    }

    private record Saved(Long messageId, String ticketNumber, String identityLine) {
    }

    private Saved saveUserMessage(AgentContext ctx, Long ticketId, String text, boolean internal) {
        Ticket ticket = ticket(ticketId);
        AppUser author = users.findById(ctx.userId()).orElseThrow(() -> new NotFoundException("User", ctx.userId()));
        Instant now = Instant.now();
        TicketMessage m = messages.save(TicketMessage.text(ticket, author,
                ctx.isCustomer() ? AuthorType.CUSTOMER : AuthorType.STAFF, text.strip(), internal, now));
        if (ticket.getStatus() == TicketStatus.RESOLVED) ticket.changeStatus(TicketStatus.OPEN, now);
        else ticket.touch(now);
        messages.flush();
        return new Saved(m.getId(), ticket.getTicketNumber(), identityLine(ctx, ticket, author));
    }

    /** Role and first name, plus the ticket for staff; no internal ids beyond what the staff tools need. */
    private static String identityLine(AgentContext ctx, Ticket ticket, AppUser user) {
        String name = firstName(user);
        if (ctx.isCustomer()) {
            return "You are talking with a customer named " + name + ". Their own orders are available through "
                    + "get_my_orders.";
        }
        return "You are assisting a support agent (" + ctx.role() + ") named " + name + " on ticket "
                + ticket.getTicketNumber() + " (status " + ticket.getStatus() + ", category " + ticket.getCategory()
                + "). The ticket's customer has id " + ticket.getCustomer().getId() + " for find_customer_orders. "
                + "Your replies here are internal notes for staff and are not shown to the customer.";
    }

    private static String firstName(AppUser user) {
        String full = user.getFullName();
        if (full != null && !full.isBlank()) return full.strip().split("\\s+")[0];
        return user.getUsername();
    }

    // ------------------------------------------------------------------ timeline

    /**
     * Messages oldest to newest. ENVELOPE payloads get their buttons' current states from {@code ai_action}
     * (USED/EXPIRED/SUPERSEDED), so old buttons render disabled. Internal notes are hidden from customers.
     */
    public PageResponse<TimelineMessage> timeline(AgentContext ctx, Long ticketId, int page, int size) {
        requireAi();
        requireChatRole(ctx);
        ticketAccess.get(ticketId);
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id")));
        actionService.expireStale(ticketId);
        return readTx.execute(s -> {
            var rows = ctx.isCustomer() ? messages.findByTicketIdAndInternalFalse(ticketId, pageable)
                    : messages.findByTicketId(ticketId, pageable);
            Map<String, AiAction> byToken = actionsByToken(ticketId, rows.getContent());
            return PageResponse.of(rows, m -> toTimeline(m, byToken));
        });
    }

    private TimelineMessage toTimeline(TicketMessage m, Map<String, AiAction> byToken) {
        JsonNode payload = m.getPayload() == null ? null : m.getPayload().deepCopy();
        if (payload != null && m.getKind() == MessageKind.ENVELOPE) refreshStates(payload, byToken);
        return new TimelineMessage(m.getId(), m.getKind(), m.getAuthorType(), authorName(m), m.getBody(),
                m.isInternal(), m.getCreatedAt(), m.getSchemaVersion(), payload);
    }

    private static String authorName(TicketMessage m) {
        if (m.getAuthorType() == AuthorType.AI) return "Assistant";
        if (m.getAuthor() == null) return m.getAuthorType() == AuthorType.SYSTEM ? "System" : null;
        String full = m.getAuthor().getFullName();
        return full != null && !full.isBlank() ? full : m.getAuthor().getUsername();
    }

    /** The ticket's issued actions behind the tokens found in these messages' payloads, keyed by raw token. */
    private Map<String, AiAction> actionsByToken(Long ticketId, List<TicketMessage> rows) {
        List<String> tokens = new ArrayList<>();
        for (TicketMessage m : rows) {
            if (m.getKind() == MessageKind.ENVELOPE && m.getPayload() != null) collectTokens(m.getPayload(), tokens);
        }
        if (tokens.isEmpty()) return Map.of();
        Map<String, String> tokenByHash = tokens.stream().distinct()
                .collect(Collectors.toMap(ActionTokenService::hash, Function.identity()));
        Map<String, AiAction> out = new HashMap<>();
        for (AiAction a : actionRepository.findByTokenHashIn(tokenByHash.keySet())) {
            if (Objects.equals(a.getTicketId(), ticketId)) out.put(tokenByHash.get(a.getTokenHash()), a);
        }
        return out;
    }

    private static void collectTokens(JsonNode node, List<String> out) {
        if (node.isObject()) {
            JsonNode token = node.get("token");
            if (token != null && token.isString() && node.has("state")) out.add(token.asString());
            node.values().forEach(child -> collectTokens(child, out));
        } else if (node.isArray()) {
            node.forEach(child -> collectTokens(child, out));
        }
    }

    private static void refreshStates(JsonNode node, Map<String, AiAction> byToken) {
        if (node instanceof ObjectNode obj) {
            JsonNode token = obj.get("token");
            if (token != null && token.isString() && obj.has("state")) {
                AiAction a = byToken.get(token.asString());
                if (a != null) obj.put("state", a.getState().name());
            }
            obj.values().forEach(child -> refreshStates(child, byToken));
        } else if (node.isArray()) {
            node.forEach(child -> refreshStates(child, byToken));
        }
    }

    // ------------------------------------------------------------------ close

    /** Marks the ticket RESOLVED (see the class comment); idempotent, 409 while a reply is being written. */
    public ConversationClosed close(AgentContext ctx, Long ticketId) {
        requireAi();
        requireChatRole(ctx);
        ticketAccess.get(ticketId);
        if (activeRuns.isActive(ticketId)) {
            throw new ConversationExceptions.Conflict("A reply is still being written in this ticket");
        }
        return tx.execute(s -> {
            Ticket t = ticket(ticketId);
            if (t.getStatus() != TicketStatus.RESOLVED && t.getStatus() != TicketStatus.CLOSED) {
                t.changeStatus(TicketStatus.RESOLVED, Instant.now());
            }
            return new ConversationClosed(t.getId(), t.getTicketNumber(), t.getStatus());
        });
    }

    // ------------------------------------------------------------------ helpers

    private Ticket ticket(Long ticketId) {
        return tickets.findById(ticketId).orElseThrow(() -> new NotFoundException("Ticket", ticketId));
    }

    private void requireAi() {
        if (streamingModel.getIfAvailable() == null) {
            throw new ConversationExceptions.AiUnavailable("The AI assistant is not enabled on this server "
                    + "(app.ai.enabled=false).");
        }
    }

    private static void requireChatRole(AgentContext ctx) {
        if (ctx.isSeller()) throw new ConversationExceptions.NotAllowed("Sellers cannot use the assistant yet");
    }
}
