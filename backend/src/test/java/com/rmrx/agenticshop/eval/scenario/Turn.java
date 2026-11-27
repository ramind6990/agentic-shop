package com.rmrx.agenticshop.eval.scenario;

import java.util.List;

/**
 * One message of the user, and the answers to give, in order, each time the run pauses on a question or approval.
 *
 * @param whilePaused documents that the previous turn left its run paused (a {@code leave} answer), so this message
 *                    abandons it; the harness checks that the run really was paused
 */
public record Turn(String user, Boolean whilePaused, List<ScriptedAnswer> answers) {

    public Turn {
        if (user == null || user.isBlank()) throw new IllegalArgumentException("turn: user text is required");
        whilePaused = Boolean.TRUE.equals(whilePaused);
        answers = answers == null ? List.of() : List.copyOf(answers);
    }
}
