package com.rmrx.agenticshop.ai.contract.response;

/**
 * A button on a block or list item. token is opaque to the client and resolved server-side, so the client never
 * sends ids or amounts back. requiresConfirmation means using it leads to a confirmation step, not the write itself.
 */
public record ItemAction(String token, ActionType type, String label, boolean requiresConfirmation,
                         ActionState state) {
}
