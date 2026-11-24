package com.rmrx.agenticshop.ai.tools;

import com.rmrx.agenticshop.ai.context.AgentContext;
import com.rmrx.agenticshop.ai.contract.tool.ToolErrorCode;
import com.rmrx.agenticshop.ai.contract.tool.ToolResult;
import com.rmrx.agenticshop.ai.tools.port.PortException;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

import java.util.function.Supplier;

/**
 * Base of the {@code @Tool} classes. An instance is built per turn with the caller's {@link AgentContext} bound, so
 * the model never passes whose data to use. {@link #run} turns every outcome into a {@link ToolResult} JSON string:
 * data is validated and registered in the turn's {@link TurnResultRegistry}; port failures, invalid input, malformed
 * upstream data and unexpected errors all become {@code Failure}. Nothing is thrown to the model.
 */
@Slf4j
abstract class AbstractTools {

    /** Items per page of every list tool. */
    static final int PAGE_SIZE = 10;

    protected final AgentContext ctx;
    private final TurnResultRegistry registry;
    private final JsonMapper json;

    AbstractTools(AgentContext ctx, TurnResultRegistry registry, JsonMapper json) {
        this.ctx = ctx;
        this.registry = registry;
        this.json = json;
    }

    protected String run(String tool, Supplier<?> call) {
        ToolResult<?> result;
        try {
            Object data = call.get();
            ResultValidator.check(data);
            result = ToolResult.ok(registry.register(tool, data), data);
        } catch (PortException e) {
            result = ToolResult.failure(e.code(), e.getMessage());
        } catch (ResultValidator.MalformedResultException e) {
            log.warn("Tool {} got a malformed upstream response: {}", tool, e.getMessage());
            result = ToolResult.failure(ToolErrorCode.UNAVAILABLE, "malformed upstream response");
        } catch (RuntimeException e) {
            log.error("Tool {} failed for user {}", tool, ctx.userId(), e);
            result = ToolResult.failure(ToolErrorCode.UNAVAILABLE, "internal error, try again later");
        }
        return json.writeValueAsString(result);
    }

    static void requireId(String name, Long id) {
        if (id == null || id <= 0) throw PortException.invalid(name + " must be a positive number");
    }

    static int page(Integer page) {
        if (page == null) return 1;
        if (page < 1) throw PortException.invalid("page must be 1 or greater");
        return page;
    }
}
