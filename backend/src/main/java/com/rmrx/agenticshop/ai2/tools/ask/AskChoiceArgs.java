package com.rmrx.agenticshop.ai2.tools.ask;

import java.util.List;

/**
 * The arguments of an {@code ask_choice} call, read back from the model's JSON. Field names match the parameters of
 * {@link AskUserTools#askChoice}, which define the schema the model sees.
 */
public record AskChoiceArgs(String question, List<ChoiceOption> options, boolean multiSelect) {
}
