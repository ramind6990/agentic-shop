package com.rmrx.agenticshop.ai.contract.tool;

/**
 * Why a tool call failed, in terms the agent can act on. Out-of-scope rows are reported as NOT_FOUND, like the REST
 * API's 404, so a tool never confirms that someone else's data exists.
 */
public enum ToolErrorCode {
    NOT_FOUND,
    FORBIDDEN,
    INELIGIBLE,
    CONFLICT,
    LIMIT_EXCEEDED,
    UNAVAILABLE,
    INVALID_INPUT,
    TIMEOUT
}
