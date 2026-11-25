package com.rmrx.agenticshop.ai.memory;

import com.rmrx.agenticshop.ai.config.AiConfig;
import com.rmrx.agenticshop.ai.config.AiProperties;
import com.rmrx.agenticshop.ai.graph.HistoryLoader;
import com.rmrx.agenticshop.ai.graph.PromptRegistry;
import com.rmrx.agenticshop.ai.trace.TraceRecorder;
import com.rmrx.agenticshop.ai.usage.RunScope;
import com.rmrx.agenticshop.ai.usage.RunScopes;
import com.rmrx.agenticshop.ai.usage.entity.AiUsagePurpose;
import com.rmrx.agenticshop.app.support.entity.TicketMessage;
import com.rmrx.agenticshop.app.support.repository.TicketMessageRepository;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Maintains the rolling summary of a ticket ({@link ConversationSummary}), called after a turn's reply was persisted.
 *
 * <p>Policy: let {@code uncovered} be the messages of the audience's view after the summary cursor. When there are
 * more than {@link AiProperties.Agent#effectiveSummaryTrigger()} of them, the oldest ones (all but the last
 * {@code historyWindow}, at most {@link #MAX_FOLD} per call) are folded in: one call to the summary model with the old
 * summary plus only those messages (incremental, never the whole thread), the result clipped to
 * {@code summaryMaxChars}, saved with the cursor moved to the last folded id. A concurrent fold of the same row loses
 * on the optimistic version and is skipped.
 *
 * <p>Visibility: the summary of a customer turn (audience CUSTOMER) is built only from customer-visible messages, the
 * one of a staff turn (STAFF) from all messages; each audience has its own row and only its own turns update it. So
 * an internal note can reach a staff prompt through the STAFF summary, never a customer prompt.
 *
 * <p>Billing and trace: the model call runs in the turn's RunScope with purpose SUMMARY (ticket, user and run set);
 * the usage listener writes its MODEL step, and a {@code summary:<audience>} step records the fold or its failure.
 * Nothing here ever throws: a failed summary leaves the old one in place and the turn is unaffected.
 */
@Slf4j
@Service
public class ConversationSummarizer {

    /** Most messages folded in one call; a longer backlog is folded over the next turns. */
    static final int MAX_FOLD = 40;
    /** Max characters of one transcript line sent to the summarizer. */
    static final int MAX_LINE = 800;
    public static final String FAILED = "SUMMARY_FAILED";

    private final TicketMessageRepository messages;
    private final ConversationSummaryRepository summaries;
    private final HistoryLoader history;
    private final PromptRegistry prompts;
    private final ObjectProvider<ChatModel> summaryModel;
    private final TraceRecorder trace;
    private final AiProperties props;
    private final TransactionTemplate readTx;
    private final TransactionTemplate tx;

    public ConversationSummarizer(TicketMessageRepository messages, ConversationSummaryRepository summaries,
                                  HistoryLoader history, PromptRegistry prompts,
                                  @Qualifier(AiConfig.SUMMARY_MODEL) ObjectProvider<ChatModel> summaryModel,
                                  TraceRecorder trace, AiProperties props, PlatformTransactionManager tm) {
        this.messages = messages;
        this.summaries = summaries;
        this.history = history;
        this.prompts = prompts;
        this.summaryModel = summaryModel;
        this.trace = trace;
        this.props = props;
        this.readTx = new TransactionTemplate(tm);
        this.readTx.setReadOnly(true);
        this.tx = new TransactionTemplate(tm);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** What the next fold would cover; null when nothing is due. */
    private record Plan(ConversationSummary current, List<TicketMessage> overflow, List<String> lines) {
    }

    /**
     * Folds the overflow of the turn's audience view into the summary if it is due. Bills to {@code scope} (the
     * turn's scope; the purpose is set to SUMMARY here). Never throws.
     */
    public void afterTurn(RunScope scope, Long ticketId, SummaryAudience audience) {
        AiProperties.Agent agent = props.agent();
        if (!agent.summaryEnabled() || agent.historyWindow() <= 0 || ticketId == null) return;
        long t0 = System.nanoTime();
        UUID runId = scope.runId();
        String step = "summary:" + audience;
        Plan plan = null;
        try {
            plan = plan(ticketId, audience, agent);
            if (plan == null) return;
            ChatModel model = summaryModel.getIfAvailable();
            if (model == null) throw new IllegalStateException("no summary model (AI disabled)");
            PromptRegistry.Prompt prompt = prompts.summary();
            int maxChars = agent.summaryMaxChars();
            String old = plan.current() == null ? "(none yet)" : plan.current().getSummary();
            ChatRequest request = ChatRequest.builder()
                    .messages(SystemMessage.from(prompt.text().replace("{{maxChars}}", String.valueOf(maxChars))),
                            UserMessage.from("Current summary:\n" + old + "\n\nNew messages (oldest first):\n"
                                    + String.join("\n", plan.lines())))
                    .build();
            String text = RunScopes.get(scope.withPurpose(AiUsagePurpose.SUMMARY), () -> model.chat(request))
                    .aiMessage().text();
            if (text == null || text.isBlank()) throw new IllegalStateException("empty summary");
            String summary = clip(text.strip(), maxChars);
            long coversUpTo = plan.overflow().getLast().getId();
            int folded = plan.overflow().size();
            save(ticketId, audience, plan.current(), summary, coversUpTo, folded, prompt.id());
            int ms = ms(t0);
            log.info("Ticket {} {} summary: folded {} messages up to id {} ({} chars, {} ms)", ticketId, audience,
                    folded, coversUpTo, summary.length(), ms);
            if (runId != null) {
                trace.recordToolStep(runId, step, "fold " + folded + " messages (ids "
                                + plan.overflow().getFirst().getId() + ".." + coversUpTo + ") into the summary",
                        "covers up to message " + coversUpTo + ", " + summary.length() + " chars, prompt "
                                + prompt.id(), null, ms);
            }
        } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException e) {
            log.info("Ticket {} {} summary changed concurrently; fold skipped", ticketId, audience);
            if (runId != null) trace.recordToolStep(runId, step, describe(plan), "concurrent update; skipped",
                    "SUMMARY_CONFLICT", ms(t0));
        } catch (Throwable e) {
            log.warn("Ticket {} {} summary failed (the turn is unaffected): {}", ticketId, audience, e.toString());
            if (runId != null) trace.recordToolStep(runId, step, describe(plan),
                    TraceRecorder.truncate(String.valueOf(e.getMessage()), 300), FAILED, ms(t0));
        }
    }

    private Plan plan(Long ticketId, SummaryAudience audience, AiProperties.Agent agent) {
        return readTx.execute(status -> {
            ConversationSummary current = summaries.find(ticketId, audience).orElse(null);
            long after = current == null ? 0L : current.getCoversUpToMessageId();
            boolean visibleOnly = audience.customerVisibleOnly();
            long uncovered = visibleOnly
                    ? messages.countByTicketIdAndInternalFalseAndIdGreaterThan(ticketId, after)
                    : messages.countByTicketIdAndIdGreaterThan(ticketId, after);
            if (uncovered <= agent.effectiveSummaryTrigger()) return null;
            int fold = (int) Math.min(MAX_FOLD, uncovered - agent.historyWindow());
            PageRequest page = PageRequest.of(0, fold, Sort.by(Sort.Order.asc("id")));
            List<TicketMessage> overflow = visibleOnly
                    ? messages.findByTicketIdAndInternalFalseAndIdGreaterThan(ticketId, after, page)
                    : messages.findByTicketIdAndIdGreaterThan(ticketId, after, page);
            if (overflow.isEmpty()) return null;
            // defence in depth: a customer summary must never see an internal note
            if (visibleOnly && overflow.stream().anyMatch(TicketMessage::isInternal)) {
                throw new IllegalStateException("internal message in a customer-visible view");
            }
            List<String> lines = overflow.stream()
                    .map(m -> TraceRecorder.truncate(history.transcriptLine(m), MAX_LINE))
                    .collect(Collectors.toList());
            return new Plan(current, overflow, lines);
        });
    }

    private void save(Long ticketId, SummaryAudience audience, ConversationSummary current, String summary,
                      long coversUpTo, int folded, String promptVersion) {
        String model = props.chat().summaryModel();
        Instant now = Instant.now();
        tx.executeWithoutResult(status -> {
            if (current == null) {
                if (summaries.existsById(new ConversationSummary.Key(ticketId, audience))) {
                    throw new DataIntegrityViolationException("summary created concurrently");
                }
                summaries.saveAndFlush(ConversationSummary.create(ticketId, audience, summary, coversUpTo, folded,
                        model, promptVersion, now));
            } else {
                // the detached row carries the version read when planning; a newer one fails the merge
                current.fold(summary, coversUpTo, folded, model, promptVersion, now);
                summaries.saveAndFlush(current);
            }
        });
    }

    private static String describe(Plan plan) {
        if (plan == null) return "plan";
        return "fold " + plan.overflow().size() + " messages up to id " + plan.overflow().getLast().getId();
    }

    /** Clips at the last line break (or space) before max, so a bullet is not cut mid-word when avoidable. */
    static String clip(String s, int max) {
        if (s.length() <= max) return s;
        String cut = s.substring(0, Math.max(1, max - 1));
        int nl = cut.lastIndexOf('\n');
        if (nl > max / 2) return cut.substring(0, nl).stripTrailing();
        int sp = cut.lastIndexOf(' ');
        return (sp > max / 2 ? cut.substring(0, sp) : cut).stripTrailing() + "…";
    }

    private static int ms(long t0) {
        return (int) ((System.nanoTime() - t0) / 1_000_000);
    }
}
