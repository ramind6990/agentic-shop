package com.rmrx.agenticshop.eval.scenario;

import java.util.Map;

/**
 * The user's scripted reaction to one pause. Exactly one kind is set:
 * <ul>
 * <li>{@code approval: {tool, args, approve}}: an approval of that tool call ({@code tool}/{@code args} must match the
 *     model's call when given; {@code args} compares only the listed arguments);</li>
 * <li>{@code choice: {value | label | index}}: picks an option of {@code ask_choice} by its exact value, by a text
 *     found (case-insensitive) in its value, label or description ({@code a|b} = either), or by 1-based position;</li>
 * <li>{@code input: {text}}, {@code yesNo: {yes}}: answers {@code ask_input} / {@code ask_yes_no};</li>
 * <li>{@code dismiss: true}: closes whatever is asked without answering;</li>
 * <li>{@code leave: true}: does not answer; the turn ends with its run paused (the next turn abandons it).</li>
 * </ul>
 * {@code optional: true} marks an answer for a pause the agent may or may not make (e.g. asking which address before
 * the approval): when the pause does not fit it, it is skipped and the next answer is tried.
 */
public record ScriptedAnswer(Approval approval, Choice choice, Input input, YesNo yesNo, Boolean dismiss,
                             Boolean leave, Boolean optional) {

    public ScriptedAnswer {
        int kinds = (approval == null ? 0 : 1) + (choice == null ? 0 : 1) + (input == null ? 0 : 1)
                + (yesNo == null ? 0 : 1) + (Boolean.TRUE.equals(dismiss) ? 1 : 0) + (Boolean.TRUE.equals(leave) ? 1 : 0);
        if (kinds != 1) throw new IllegalArgumentException("an answer needs exactly one of approval, choice, input, "
                + "yesNo, dismiss, leave");
    }

    public boolean isDismiss() {
        return Boolean.TRUE.equals(dismiss);
    }

    public boolean isLeave() {
        return Boolean.TRUE.equals(leave);
    }

    public boolean isOptional() {
        return Boolean.TRUE.equals(optional);
    }

    public record Approval(String tool, Map<String, Object> args, boolean approve) {
    }

    public record Choice(String value, String label, Integer index) {
    }

    public record Input(String text) {
    }

    public record YesNo(boolean yes) {
    }
}
