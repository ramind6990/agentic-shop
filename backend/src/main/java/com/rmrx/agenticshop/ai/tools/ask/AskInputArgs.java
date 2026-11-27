package com.rmrx.agenticshop.ai.tools.ask;

/**
 * The arguments of an {@code ask_input} call, read back from the model's JSON. Field names match the parameters of
 * {@link AskUserTools#askInput}, which define the schema the model sees.
 */
public record AskInputArgs(String question) {
}
