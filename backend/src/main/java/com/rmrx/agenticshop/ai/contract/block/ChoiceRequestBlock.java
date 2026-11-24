package com.rmrx.agenticshop.ai.contract.block;

import com.rmrx.agenticshop.ai.contract.response.ItemAction;

import java.util.List;

/** Asks the user to pick one option, e.g. which order they mean. Each option carries a SELECT_OPTION action. */
public record ChoiceRequestBlock(String id, String prompt, List<Option> options) implements Block {

    public ChoiceRequestBlock {
        options = options == null ? List.of() : List.copyOf(options);
    }

    public record Option(String label, String description, ItemAction action) {
    }
}
