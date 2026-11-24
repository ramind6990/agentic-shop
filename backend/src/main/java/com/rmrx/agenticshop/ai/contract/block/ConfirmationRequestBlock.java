package com.rmrx.agenticshop.ai.contract.block;

import com.rmrx.agenticshop.ai.contract.response.ItemAction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Asks the user to confirm a write before it happens. target summarizes what is affected (e.g. order number and
 * items); amount and currency are null when no money moves; policyReason says why confirmation is needed. actions
 * are normally CONFIRM and DECLINE, valid until expiresAt.
 */
public record ConfirmationRequestBlock(String id, String title, String description, String target,
                                       BigDecimal amount, String currency, String policyReason, Instant expiresAt,
                                       List<ItemAction> actions) implements Block {

    public ConfirmationRequestBlock {
        actions = actions == null ? List.of() : List.copyOf(actions);
    }
}
