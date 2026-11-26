package com.rmrx.agenticshop.ai2.web;

import com.rmrx.agenticshop.ai2.conversation.ConversationConflictException;
import com.rmrx.agenticshop.ai2.conversation.ConversationDetail;
import com.rmrx.agenticshop.ai2.conversation.store.ConversationSummary;
import com.rmrx.agenticshop.ai2.conversation.ConversationService;
import com.rmrx.agenticshop.ai2.conversation.RunOutcome;
import com.rmrx.agenticshop.ai2.conversation.message.ConversationMessage;
import com.rmrx.agenticshop.app.security.AccessScope;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Conversations with the ai2 agent. Sending and answering return at once (with the {@code conversationId}):
 * {@code running} — the run executes in the background, its progress and its end (the reply, or the question or
 * approval to answer) arrive on {@code /api/ai2/runs/{runId}/events} — or {@code abandoned}. 404 for a conversation
 * that is not the caller's; 409 while a reply is still being prepared, when there is no question to answer, or when
 * the conversation is full.
 */
// TODO the response is the raw run outcome until the reply envelope exists.
@RestController("ai2ConversationController")
@RequestMapping("/api/ai2/conversations")
@PreAuthorize("hasAuthority('AI_CHAT')")
public class ConversationController {

    private final ConversationService conversations;
    private final AccessScope scope;

    public ConversationController(ConversationService conversations, AccessScope scope) {
        this.conversations = conversations;
        this.scope = scope;
    }

    /** The caller's conversations, most recently active first. */
    @GetMapping
    public List<ConversationSummary> list() {
        return conversations.list(scope.current().id());
    }

    /** One conversation: title, status, active run and timeline. */
    @GetMapping("/{conversationId}")
    public ConversationDetail detail(@PathVariable Long conversationId) {
        return conversations.detail(scope.current().id(), conversationId);
    }

    /** The first message: creates the conversation. */
    @PostMapping("/messages")
    public RunOutcome start(@Valid @RequestBody SendMessageRequest request) {
        return conversations.sendMessage(scope.current().id(), null, request.text(), null);
    }

    @GetMapping("/{conversationId}/messages")
    public List<ConversationMessage> timeline(@PathVariable Long conversationId) {
        return conversations.timeline(scope.current().id(), conversationId);
    }

    @PostMapping("/{conversationId}/messages")
    public RunOutcome send(@PathVariable Long conversationId, @Valid @RequestBody SendMessageRequest request) {
        return conversations.sendMessage(scope.current().id(), conversationId, request.text(), null);
    }

    @PostMapping("/{conversationId}/answers")
    public RunOutcome answer(@PathVariable Long conversationId, @Valid @RequestBody AnswerRequest request) {
        return conversations.answer(scope.current().id(), conversationId, request.interactionId(), request.answer(),
                null);
    }

    @ExceptionHandler(ConversationConflictException.class)
    ProblemDetail conflict(ConversationConflictException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("Conflict");
        return problem;
    }
}
