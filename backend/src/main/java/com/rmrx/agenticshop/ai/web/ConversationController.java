package com.rmrx.agenticshop.ai.web;

import com.rmrx.agenticshop.ai.action.ActionNotFoundException;
import com.rmrx.agenticshop.ai.context.AgentContext;
import com.rmrx.agenticshop.ai.context.AgentContextFactory;
import com.rmrx.agenticshop.ai.contract.request.ActionRequest;
import com.rmrx.agenticshop.ai.contract.request.SendMessageRequest;
import com.rmrx.agenticshop.ai.contract.request.StartConversationRequest;
import com.rmrx.agenticshop.ai.conversation.ConversationDtos.ConversationClosed;
import com.rmrx.agenticshop.ai.conversation.ConversationDtos.TimelineMessage;
import com.rmrx.agenticshop.ai.conversation.ConversationDtos.TurnAccepted;
import com.rmrx.agenticshop.ai.conversation.ConversationExceptions;
import com.rmrx.agenticshop.ai.conversation.ConversationService;
import com.rmrx.agenticshop.common.dto.PageResponse;
import com.rmrx.agenticshop.security.AccessScope;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Support conversations with the AI assistant, and the chat buttons of its replies. Sending returns 202 with the runId at once; the reply streams on
 * {@code GET /api/ai/runs/{runId}/events} and is then part of the timeline. 404 for tickets the caller cannot see,
 * 409 while another reply in the ticket is in progress (or the ticket is closed), 503 when AI is disabled. On a ticket
 * handed to a human (ESCALATED), a customer's message is stored and answered 202 with {@code runId: null,
 * escalated: true} (no AI turn), and a customer's chat button is 409 with {@code code: TICKET_ESCALATED}.
 */
@RestController
@RequestMapping("/api/ai")
@PreAuthorize("hasAuthority('AI_CHAT')")
public class ConversationController {

    private final ConversationService conversations;
    private final AgentContextFactory contexts;
    private final AccessScope scope;

    public ConversationController(ConversationService conversations, AgentContextFactory contexts,
                                  AccessScope scope) {
        this.conversations = conversations;
        this.contexts = contexts;
        this.scope = scope;
    }

    /** Opens a ticket (customers only) with its first message. */
    @PostMapping("/conversations")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TurnAccepted start(@Valid @RequestBody StartConversationRequest req) {
        return conversations.start(context(null), req);
    }

    @PostMapping("/conversations/{ticketId}/messages")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TurnAccepted send(@PathVariable Long ticketId, @Valid @RequestBody SendMessageRequest req) {
        return conversations.send(context(ticketId), ticketId, req.text());
    }

    /** Oldest first; page 0 is the start of the conversation. */
    @GetMapping("/conversations/{ticketId}/messages")
    public PageResponse<TimelineMessage> timeline(@PathVariable Long ticketId,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "50") int size) {
        return conversations.timeline(context(ticketId), ticketId, page, size);
    }

    @PostMapping("/conversations/{ticketId}/close")
    public ConversationClosed close(@PathVariable Long ticketId) {
        return conversations.close(context(ticketId), ticketId);
    }

    /**
     * Uses a button of an earlier reply (confirm/decline a pending write, or an item action such as Track or
     * Cancel) and starts the turn answering it: 202 with the runId, streamed like a message. The token is the
     * ticket's key; any token that is not the caller's own is 404. A stale button (expired, superseded, already
     * used) still gets 202 and a reply with the current state, but never runs anything.
     */
    @PostMapping("/actions/{token}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TurnAccepted act(@PathVariable String token, @Valid @RequestBody(required = false) ActionRequest req) {
        return conversations.act(context(null), token, req == null ? new ActionRequest(null, null) : req);
    }

    @ExceptionHandler(ActionNotFoundException.class)
    ProblemDetail actionNotFound(ActionNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Not found", ActionNotFoundException.MESSAGE);
    }

    @ExceptionHandler(ConversationExceptions.TicketEscalated.class)
    ProblemDetail escalated(ConversationExceptions.TicketEscalated ex) {
        ProblemDetail pd = problem(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
        pd.setProperty("code", ConversationExceptions.TicketEscalated.CODE);
        return pd;
    }

    @ExceptionHandler(ConversationExceptions.Conflict.class)
    ProblemDetail conflict(ConversationExceptions.Conflict ex) {
        return problem(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
    }

    @ExceptionHandler(ConversationExceptions.AiUnavailable.class)
    ProblemDetail unavailable(ConversationExceptions.AiUnavailable ex) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "AI unavailable", ex.getMessage());
    }

    @ExceptionHandler(ConversationExceptions.NotAllowed.class)
    ProblemDetail notAllowed(ConversationExceptions.NotAllowed ex) {
        return problem(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage());
    }

    /** Built on the request thread, where the security context exists. */
    private AgentContext context(Long ticketId) {
        return contexts.from(scope.current(), ticketId);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        return pd;
    }
}
