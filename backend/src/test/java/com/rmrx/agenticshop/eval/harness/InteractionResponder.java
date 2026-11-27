package com.rmrx.agenticshop.eval.harness;

import com.rmrx.agenticshop.ai.contract.interaction.HumanAnswer;
import com.rmrx.agenticshop.ai.graph.agent.pending.PendingInteraction;
import com.rmrx.agenticshop.ai.graph.agent.pending.PendingInteraction.ApprovalRequest;
import com.rmrx.agenticshop.ai.graph.agent.pending.PendingInteraction.ChoiceQuestion;
import com.rmrx.agenticshop.ai.graph.agent.pending.PendingInteraction.InputQuestion;
import com.rmrx.agenticshop.ai.graph.agent.pending.PendingInteraction.YesNoQuestion;
import com.rmrx.agenticshop.ai.tools.ask.ChoiceOption;
import com.rmrx.agenticshop.eval.scenario.ScriptedAnswer;

import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Picks the user's reaction to a pause from the turn's scripted answers, in order. The next scripted answer is used
 * when it fits the pause (kind, and for an approval the tool and the listed arguments of the model's call). A pause it
 * does not fit is unexpected: it is dismissed so the run can end, and the scripted answer stays for a later pause.
 */
final class InteractionResponder {

    /**
     * @param answer   what to send; null = leave the run paused
     * @param mismatch why the pause was unexpected, or null
     */
    record Decision(HumanAnswer answer, ScriptedAnswer scripted, String mismatch) {
    }

    Decision decide(PendingInteraction pending, Optional<ToolCallView> call, Deque<ScriptedAnswer> script) {
        while (true) {
            ScriptedAnswer next = script.peek();
            if (next == null) return unexpected("no scripted answer left for this " + kind(pending) + describe(pending, call));
            Optional<HumanAnswer> answer = answer(next, pending, call);
            if (answer == null && next.isOptional()) {
                script.poll();
                continue;
            }
            if (answer == null) return unexpected("the next scripted answer does not fit this " + kind(pending)
                    + describe(pending, call));
            script.poll();
            return new Decision(answer.orElse(null), next, null);
        }
    }

    private static Decision unexpected(String why) {
        return new Decision(new HumanAnswer.Dismissed(), null, why);
    }

    /** The answer; Optional.empty() = leave paused; null = does not fit. */
    private static Optional<HumanAnswer> answer(ScriptedAnswer s, PendingInteraction pending, Optional<ToolCallView> call) {
        if (s.isLeave()) return Optional.empty();
        if (s.isDismiss()) return Optional.of(new HumanAnswer.Dismissed());
        if (s.approval() != null && pending instanceof ApprovalRequest) {
            return approvalFits(s.approval(), call) ? Optional.of(new HumanAnswer.ApprovalDecision(s.approval().approve()))
                    : null;
        }
        if (s.choice() != null && pending instanceof ChoiceQuestion q) {
            return pick(s.choice(), q.options())
                    .map(o -> Optional.<HumanAnswer>of(new HumanAnswer.ChoiceAnswer(List.of(o.value()))))
                    .orElse(null);
        }
        if (s.input() != null && pending instanceof InputQuestion) return Optional.of(new HumanAnswer.InputAnswer(s.input().text()));
        if (s.yesNo() != null && pending instanceof YesNoQuestion) return Optional.of(new HumanAnswer.YesNoAnswer(s.yesNo().yes()));
        return null;
    }

    private static boolean approvalFits(ScriptedAnswer.Approval expected, Optional<ToolCallView> call) {
        if (expected.tool() == null && (expected.args() == null || expected.args().isEmpty())) return true;
        if (call.isEmpty()) return false;
        if (expected.tool() != null && !expected.tool().equals(call.get().name())) return false;
        if (expected.args() == null) return true;
        Map<String, Object> actual = call.get().args();
        return expected.args().entrySet().stream().allMatch(e -> same(e.getValue(), actual.get(e.getKey())));
    }

    private static boolean same(Object expected, Object actual) {
        if (expected == null || actual == null) return Objects.equals(expected, actual);
        return normalize(expected).equals(normalize(actual));
    }

    private static String normalize(Object value) {
        return String.valueOf(value).trim().toLowerCase(Locale.ROOT);
    }

    private static Optional<ChoiceOption> pick(ScriptedAnswer.Choice choice, List<ChoiceOption> options) {
        if (choice.index() != null) {
            int i = choice.index() - 1;
            return i >= 0 && i < options.size() ? Optional.of(options.get(i)) : Optional.empty();
        }
        if (choice.value() != null) {
            return options.stream().filter(o -> o.value().equalsIgnoreCase(choice.value().trim())).findFirst();
        }
        if (choice.label() != null) {
            List<String> parts = java.util.Arrays.stream(choice.label().toLowerCase(Locale.ROOT).split("\\|"))
                    .map(String::trim).filter(p -> !p.isEmpty()).toList();
            return options.stream()
                    .filter(o -> {
                        String text = (o.value() + " " + o.label() + " " + Objects.toString(o.description(), ""))
                                .toLowerCase(Locale.ROOT);
                        return parts.stream().anyMatch(text::contains);
                    })
                    .findFirst();
        }
        return Optional.empty();
    }

    private static String kind(PendingInteraction pending) {
        return switch (pending) {
            case ApprovalRequest a -> "approval";
            case ChoiceQuestion q -> "choice question";
            case InputQuestion q -> "input question";
            case YesNoQuestion q -> "yes/no question";
        };
    }

    private static String describe(PendingInteraction pending, Optional<ToolCallView> call) {
        return " (" + pending.promptText() + call.map(c -> "; tool " + c.name() + " " + c.args()).orElse("") + ")";
    }
}
