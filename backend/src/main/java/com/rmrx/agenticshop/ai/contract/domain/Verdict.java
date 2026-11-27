package com.rmrx.agenticshop.ai.contract.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;

/** Whether an action is allowed now; when not, why, in words the user can understand. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Verdict(boolean allowed, String reason) implements Serializable {
}
