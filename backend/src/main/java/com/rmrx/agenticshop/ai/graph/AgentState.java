package com.rmrx.agenticshop.ai.graph;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Graph state of one support agent turn. Only serializable values live here (LangGraph4j clones the state through
 * {@code LC4jStateSerializer} between nodes, and a checkpoint saver will persist it later): the chat messages sent to
 * the model, the step count, the final assistant text and the budget flag. The caller's context, the turn's
 * {@code TurnResultRegistry}/{@code BoundTools}, the publisher and the draft response are per-turn objects in
 * {@link TurnRuntime}, reached with {@link #runtime(RunnableConfig)}.
 */
public class AgentState extends org.bsc.langgraph4j.state.AgentState {

    public static final String MESSAGES = "messages";
    public static final String STEP_COUNT = "stepCount";
    public static final String FINAL_TEXT = "finalText";
    public static final String BUDGET_EXHAUSTED = "budgetExhausted";

    /** RunnableConfig metadata key of the turn's {@link TurnRuntime}. */
    public static final String RUNTIME = "agenticshop.turnRuntime";

    /** messages is append-only; the other keys are last-write-wins. */
    public static final Map<String, Channel<?>> SCHEMA = Map.of(
            MESSAGES, Channels.appender(ArrayList::new));

    public AgentState(Map<String, Object> initData) {
        super(initData);
    }

    public List<ChatMessage> messages() {
        return this.<List<ChatMessage>>value(MESSAGES).orElseGet(List::of);
    }

    public Optional<AiMessage> lastAiMessage() {
        List<ChatMessage> messages = messages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof AiMessage ai) return Optional.of(ai);
        }
        return Optional.empty();
    }

    public int stepCount() {
        return this.<Integer>value(STEP_COUNT).orElse(0);
    }

    public Optional<String> finalText() {
        return value(FINAL_TEXT);
    }

    public boolean budgetExhausted() {
        return this.<Boolean>value(BUDGET_EXHAUSTED).orElse(false);
    }

    public static TurnRuntime runtime(RunnableConfig config) {
        return config.metadata(RUNTIME).map(TurnRuntime.class::cast)
                .orElseThrow(() -> new IllegalStateException("no TurnRuntime in the run config"));
    }
}
