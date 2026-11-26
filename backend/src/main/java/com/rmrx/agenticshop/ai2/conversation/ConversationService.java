package com.rmrx.agenticshop.ai2.conversation;

import com.rmrx.agenticshop.ai2.activerun.ActiveRun;
import com.rmrx.agenticshop.ai2.activerun.ActiveRunRegistry;
import com.rmrx.agenticshop.ai2.contract.interaction.HumanAnswer;
import com.rmrx.agenticshop.ai2.conversation.message.ConversationMessage;
import com.rmrx.agenticshop.ai2.conversation.message.ConversationMessageStore;
import com.rmrx.agenticshop.ai2.conversation.message.InteractionStatus;
import com.rmrx.agenticshop.ai2.conversation.store.Conversation;
import com.rmrx.agenticshop.ai2.conversation.store.ConversationStore;
import com.rmrx.agenticshop.ai2.graph.agent.pending.PendingInteraction;
import com.rmrx.agenticshop.ai2.graph.state.RunContext;
import com.rmrx.agenticshop.app.common.exception.NotFoundException;
import dev.langchain4j.data.message.UserMessage;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * The entry points of a conversation: a new message from the user, or the user's answer to the question or approval
 * the conversation's run is paused on. A conversation has at most one active run ({@link ActiveRunRegistry}):
 * <ul>
 * <li>while it is RUNNING, any input is rejected ({@link ConversationConflictException}) and the run continues;</li>
 * <li>while it is PAUSED, only a matching, acceptable answer resumes it; any other input abandons it — a new
 *     message then starts a new run, a non-matching answer does not.</li>
 * </ul>
 * Everything the user sees is stored as chat messages ({@link ConversationMessageStore}): the user's message, each
 * question or approval (PENDING until answered or abandoned) and the final reply. Runs execute in the background
 * ({@link RunLauncher}); the client follows them through their run events.
 */
@Service("ai2ConversationService")
public class ConversationService {

    private final ConversationStore conversations;
    private final ConversationMessageStore messages;
    private final ActiveRunRegistry activeRuns;
    private final GraphRunner runner;
    private final RunLauncher launcher;

    public ConversationService(ConversationStore conversations, ConversationMessageStore messages,
                               ActiveRunRegistry activeRuns, GraphRunner runner, RunLauncher launcher) {
        this.conversations = conversations;
        this.messages = messages;
        this.activeRuns = activeRuns;
        this.runner = runner;
        this.launcher = launcher;
    }

    /**
     * A new message: starts a run (abandoning a paused one first). Without a conversation id, a new conversation is
     * created for it.
     */
    public RunOutcome sendMessage(Long userId, Long conversationId, String text) {
        Conversation conversation = conversationId == null ? conversations.create(userId) : owned(conversationId, userId);
        if (conversation.status() == Conversation.Status.CLOSED) {
            throw new ConversationConflictException("This conversation is full; please start a new one.");
        }
        activeRuns.find(conversation.id()).ifPresent(current -> {
            if (current.status() == ActiveRun.Status.RUNNING) {
                throw new ConversationConflictException("A reply is still being prepared in this conversation.");
            }
            activeRuns.takePaused(conversation.id()).ifPresent(this::abandon);
        });
        ActiveRun run = activeRuns.tryStart(conversation.id(), userId)
                .orElseThrow(() -> new ConversationConflictException("Another run started in this conversation."));
        messages.appendUser(conversation.id(), run.runId(), text);
        conversations.touch(conversation.id());
        RunContext context = new RunContext(userId, conversation.id(), run.runId());
        return launcher.launch(run, () -> runner.start(context, List.of(UserMessage.from(text))));
    }

    /**
     * The answer to the pending question or approval: resumes the run when it matches; otherwise abandons the run
     * and starts nothing.
     */
    public RunOutcome answer(Long userId, Long conversationId, UUID interactionId, HumanAnswer answer) {
        owned(conversationId, userId);
        ActiveRun current = activeRuns.find(conversationId)
                .orElseThrow(() -> new ConversationConflictException("There is no question to answer."));
        if (current.status() == ActiveRun.Status.RUNNING) {
            throw new ConversationConflictException("A reply is still being prepared in this conversation.");
        }
        PendingInteraction pending = current.pendingInteraction();
        if (pending.interactionId().equals(interactionId) && pending.accepts(answer)) {
            ActiveRun resumed = activeRuns.tryResume(conversationId, interactionId)
                    .orElseThrow(() -> new ConversationConflictException("This question was already answered."));
            messages.resolveInteraction(interactionId, InteractionStatus.of(answer), answer);
            return launcher.launch(resumed,
                    () -> runner.resume(conversationId, resumed.runId(), pending.callId(), answer));
        }
        return activeRuns.takePaused(conversationId)
                .map(this::abandon)
                .orElseThrow(() -> new ConversationConflictException("This question was already answered."));
    }

    /** The chat timeline, oldest first. */
    public List<ConversationMessage> timeline(Long userId, Long conversationId) {
        owned(conversationId, userId);
        return messages.list(conversationId);
    }

    /** Drops a paused run already removed from the registry; its question stays in the timeline as NOT_ANSWERED. */
    private RunOutcome.Abandoned abandon(ActiveRun paused) {
        runner.discard(paused.runId());
        messages.resolveInteraction(paused.pendingInteraction().interactionId(), InteractionStatus.NOT_ANSWERED, null);
        return new RunOutcome.Abandoned(paused.conversationId(), paused.runId(), paused.pendingInteraction());
    }

    /** The conversation, when it belongs to the user; 404 otherwise (another user's conversation is not revealed). */
    private Conversation owned(Long conversationId, Long userId) {
        return conversations.findOwned(conversationId, userId)
                .orElseThrow(() -> new NotFoundException("Conversation", conversationId));
    }
}
