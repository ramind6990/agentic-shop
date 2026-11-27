package com.rmrx.agenticshop.ai2.usage;

import com.rmrx.agenticshop.ai2.conversation.store.ConversationStore;
import com.rmrx.agenticshop.ai2.memory.HistoryProvider;
import com.rmrx.agenticshop.ai2.usage.HistoryTokenCounter.HistoryTokens;
import com.rmrx.agenticshop.ai2.usage.UsageViews.ContextUsage;
import com.rmrx.agenticshop.ai2.usage.UsageViews.ConversationUsage;
import com.rmrx.agenticshop.ai2.usage.UsageViews.UserUsage;
import com.rmrx.agenticshop.app.common.exception.BadRequestException;
import com.rmrx.agenticshop.app.common.exception.NotFoundException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

/** The read side of the token ledger: a user's usage, a conversation's usage and its context. */
@Service
public class UsageService {

    private final UsageQueries queries;
    private final ConversationStore conversations;
    private final HistoryProvider history;
    private final HistoryTokenCounter tokenCounter;
    private final Ai2UsageProperties limits;

    public UsageService(UsageQueries queries, ConversationStore conversations, HistoryProvider history,
                        HistoryTokenCounter tokenCounter, Ai2UsageProperties limits) {
        this.queries = queries;
        this.conversations = conversations;
        this.history = history;
        this.tokenCounter = tokenCounter;
        this.limits = limits;
    }

    /** A user's usage over the UTC days from..to, both inclusive; without dates the current calendar month (UTC). */
    public UserUsage userUsage(Long userId, LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate start = from != null ? from : today.withDayOfMonth(1);
        LocalDate endExclusive = to != null ? to.plusDays(1) : today.withDayOfMonth(1).plusMonths(1);
        if (!start.isBefore(endExclusive)) throw new BadRequestException("'from' must not be after 'to'");
        Instant fromAt = start.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toAt = endExclusive.atStartOfDay(ZoneOffset.UTC).toInstant();
        Map<UsageIssuer, TokenTotals> byIssuer = queries.byIssuerForUser(userId, fromAt, toAt);
        return new UserUsage(userId, fromAt, toAt, TokenTotals.sum(byIssuer.values()), byIssuer);
    }

    /** The usage and context of the user's conversation; 404 when it is not theirs. */
    public ConversationUsage conversationUsage(Long userId, Long conversationId) {
        conversations.findOwned(conversationId, userId)
                .orElseThrow(() -> new NotFoundException("Conversation", conversationId));
        Map<UsageIssuer, TokenTotals> byIssuer = queries.byIssuerForConversation(conversationId);
        HistoryTokens tokens = tokenCounter.count(history.currentHistory(conversationId));
        ContextUsage context = new ContextUsage(tokens.totalTokens(), tokens.summaryTokens(),
                limits.contextTokenLimit(), history.memoryState(conversationId));
        return new ConversationUsage(conversationId, TokenTotals.sum(byIssuer.values()), byIssuer,
                limits.chatTokenLimit(), context);
    }
}
