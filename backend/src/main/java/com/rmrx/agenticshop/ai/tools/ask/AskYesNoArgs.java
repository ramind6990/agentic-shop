package com.rmrx.agenticshop.ai.tools.ask;

/**
 * The arguments of an {@code ask_yes_no} call, read back from the model's JSON. Field names match the parameters of
 * {@link AskUserTools#askYesNo}, which define the schema the model sees.
 */
public record AskYesNoArgs(String question) {
}
