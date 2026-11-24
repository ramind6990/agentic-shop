package com.rmrx.agenticshop.ai.tools.port;

import com.rmrx.agenticshop.ai.contract.tool.ToolErrorCode;

/**
 * How every port reports a failure: a typed code the tool turns into {@code ToolResult.Failure}. Ports return plain
 * data on success and throw this otherwise; rows outside the caller's scope are NOT_FOUND.
 */
public class PortException extends RuntimeException {

    private final ToolErrorCode code;

    public PortException(ToolErrorCode code, String message) {
        super(message, null, false, false);
        this.code = code;
    }

    public ToolErrorCode code() {
        return code;
    }

    public static PortException notFound(String what, Object id) {
        return new PortException(ToolErrorCode.NOT_FOUND, what + " " + id + " not found");
    }

    public static PortException invalid(String message) {
        return new PortException(ToolErrorCode.INVALID_INPUT, message);
    }

    public static PortException forbidden(String message) {
        return new PortException(ToolErrorCode.FORBIDDEN, message);
    }

    public static PortException ineligible(String message) {
        return new PortException(ToolErrorCode.INELIGIBLE, message);
    }

    public static PortException conflict(String message) {
        return new PortException(ToolErrorCode.CONFLICT, message);
    }
}
