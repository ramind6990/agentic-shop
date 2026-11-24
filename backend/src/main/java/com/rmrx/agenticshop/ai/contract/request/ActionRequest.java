package com.rmrx.agenticshop.ai.contract.request;

import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Use of an {@code ItemAction} the UI rendered; the action is identified by its token in the path. decision is set
 * for confirmation actions only, params carry extra input some actions need (e.g. a selected quantity).
 */
public record ActionRequest(Decision decision, @Size(max = 20) Map<String, String> params) {

    public ActionRequest {
        params = params == null ? Map.of() : Map.copyOf(params);
    }

    public enum Decision {
        CONFIRM,
        DECLINE
    }
}
