package com.rmrx.agenticshop.ai.contract.tool;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * What every tool returns: data with a resultId that blocks and citations can point back to, or a typed failure.
 * Tools return failures instead of throwing, so the agent can explain or recover.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ToolResult.Ok.class, name = "OK"),
        @JsonSubTypes.Type(value = ToolResult.Failure.class, name = "FAILURE")
})
public sealed interface ToolResult<T> {

    static <T> ToolResult<T> ok(String resultId, T data) {
        return new Ok<>(resultId, data);
    }

    static <T> ToolResult<T> failure(ToolErrorCode code, String message) {
        return new Failure<>(code, message);
    }

    @JsonIgnore
    default boolean isOk() {
        return this instanceof Ok;
    }

    record Ok<T>(String resultId, T data) implements ToolResult<T> {
    }

    record Failure<T>(ToolErrorCode code, String message) implements ToolResult<T> {
    }
}
