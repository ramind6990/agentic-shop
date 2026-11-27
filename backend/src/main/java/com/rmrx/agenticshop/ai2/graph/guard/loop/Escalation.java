package com.rmrx.agenticshop.ai2.graph.guard.loop;

import java.io.Serializable;

/**
 * A run that code (not the model) decided to stop: the loop detector found it going in circles. Kept in the run state
 * from then on.
 *
 * @param mode        HAND_OFF: the model must call {@code escalate_to_human} next (customers); ANSWER_NOW: tools off,
 *                    the model must reply with what it has (staff, or no hand-off tool)
 * @param reason      what the detector saw, told to the model
 * @param fromMessage index in the run's messages from which a hand-off result counts
 */
public record Escalation(Mode mode, String reason, int fromMessage) implements Serializable {

    public enum Mode {
        HAND_OFF, ANSWER_NOW
    }
}
