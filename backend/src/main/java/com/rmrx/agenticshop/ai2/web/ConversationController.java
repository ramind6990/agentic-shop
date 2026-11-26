package com.rmrx.agenticshop.ai2.web;

import com.rmrx.agenticshop.ai2.conversation.ConversationConflictException;
import com.rmrx.agenticshop.ai2.conversation.ConversationService;
import com.rmrx.agenticshop.ai2.conversation.RunOutcome;
import com.rmrx.agenticshop.app.security.AccessScope;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Conversations with the ai2 agent. Both calls run the graph synchronously and return how the run ended:
 * {@code completed} (with the reply), {@code paused} (with the question or approval to answer) or {@code abandoned}.
 * 409 while a reply is still being prepared, or when there is no question to answer; 403 for another user's run.
 */
// TODO the conversation id is not checked against a conversation the user owns yet (no conversation entity); the
//  response is the raw run outcome until the reply envelope exists.
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

    @PostMapping("/{conversationId}/messages")
    public RunOutcome send(@PathVariable Long conversationId, @Valid @RequestBody SendMessageRequest request) {
        return conversations.sendMessage(scope.current().id(), conversationId, request.text());
    }

    @PostMapping("/{conversationId}/answers")
    public RunOutcome answer(@PathVariable Long conversationId, @Valid @RequestBody AnswerRequest request) {
        return conversations.answer(scope.current().id(), conversationId, request.interactionId(), request.answer());
    }

    @ExceptionHandler(ConversationConflictException.class)
    ProblemDetail conflict(ConversationConflictException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("Conflict");
        return problem;
    }
}
