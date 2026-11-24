package com.rmrx.agenticshop.ai.web.dev;

import com.rmrx.agenticshop.ai.config.AiProperties;
import com.rmrx.agenticshop.ai.context.AgentContext;
import com.rmrx.agenticshop.ai.context.AgentContextFactory;
import com.rmrx.agenticshop.ai.contract.tool.ToolResult;
import com.rmrx.agenticshop.ai.tools.BoundTools;
import com.rmrx.agenticshop.ai.tools.ToolCatalog;
import com.rmrx.agenticshop.ai.tools.TurnResultRegistry;
import com.rmrx.agenticshop.ai.tools.mock.MockScenarios;
import com.rmrx.agenticshop.ai.tools.mock.MockWorlds;
import com.rmrx.agenticshop.ai.tools.port.OrderPort;
import com.rmrx.agenticshop.ai.tools.port.PortException;
import com.rmrx.agenticshop.ai.tools.port.RefundPort;
import com.rmrx.agenticshop.ai.tools.port.ReturnPort;
import com.rmrx.agenticshop.ai.tools.port.SupportPort;
import com.rmrx.agenticshop.common.exception.BadRequestException;
import com.rmrx.agenticshop.identity.entity.Role;
import com.rmrx.agenticshop.returns.entity.ReturnReason;
import com.rmrx.agenticshop.security.AccessScope;
import dev.langchain4j.internal.JsonSchemaElementJsonUtils;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Dev-only access to the tool layer (present only with {@code app.ai.dev-endpoints=true}): list and run the caller's
 * tools through the same {@link ToolCatalog} and executor path the agent will use, switch or reset the caller's mock
 * world, and call the write ports directly to check their rules before the confirmation gateway exists.
 *
 * <p>With {@code app.ai.mock.scenario-header-enabled=true}, an {@code X-Mock-Scenario} header switches the caller's
 * world to that scenario first (only if it is on another one, so state persists across calls).
 */
@RestController
@RequestMapping("/api/ai/dev")
@ConditionalOnBooleanProperty("app.ai.dev-endpoints")
public class AiDevToolsController {

    static final String SCENARIO_HEADER = "X-Mock-Scenario";

    private final ToolCatalog catalog;
    private final AgentContextFactory contexts;
    private final AccessScope scope;
    private final ObjectProvider<MockWorlds> worlds;
    private final AiProperties props;
    private final OrderPort orders;
    private final ReturnPort returns;
    private final RefundPort refunds;
    private final SupportPort support;

    public AiDevToolsController(ToolCatalog catalog, AgentContextFactory contexts, AccessScope scope,
                                ObjectProvider<MockWorlds> worlds, AiProperties props, OrderPort orders,
                                ReturnPort returns, RefundPort refunds, SupportPort support) {
        this.catalog = catalog;
        this.contexts = contexts;
        this.scope = scope;
        this.worlds = worlds;
        this.props = props;
        this.orders = orders;
        this.returns = returns;
        this.refunds = refunds;
        this.support = support;
    }

    public record ToolInfo(String name, String description, String label, Map<String, Object> parameters) {
    }

    public record ToolList(Role role, String scenario, List<ToolInfo> tools) {
    }

    public record ToolRun(String tool, String label, String scenario, String output,
                          List<TurnResultRegistry.Entry> registry) {
    }

    public record ScenarioRequest(@NotBlank String scenario) {
    }

    public record ScenarioResponse(Long userId, String scenario) {
    }

    /** Arguments of a direct write; each op uses the fields it needs. */
    public record WriteRequest(Long orderId, Long orderItemId, ReturnReason reason, String note, BigDecimal amount,
                               Long ticketId, String text) {
    }

    public record WriteResponse(String op, String scenario, ToolResult<?> result) {
    }

    @GetMapping("/tools")
    public ToolList tools(@RequestHeader(value = SCENARIO_HEADER, required = false) String scenarioHeader) {
        AgentContext ctx = context(null);
        applyScenarioHeader(ctx, scenarioHeader);
        BoundTools tools = catalog.toolsFor(ctx, new TurnResultRegistry());
        List<ToolInfo> infos = tools.all().stream()
                .map(b -> new ToolInfo(b.name(), b.specification().description(), b.label(),
                        b.specification().parameters() == null ? Map.of()
                                : JsonSchemaElementJsonUtils.toMap(b.specification().parameters())))
                .toList();
        return new ToolList(ctx.role(), scenarioOf(ctx), infos);
    }

    /** Runs one tool as the caller; the body is the JSON arguments object the model would send. */
    @PostMapping("/tools/{name}")
    public ToolRun run(@PathVariable String name, @RequestBody(required = false) String arguments,
                       @RequestParam(required = false) Long ticketId,
                       @RequestHeader(value = SCENARIO_HEADER, required = false) String scenarioHeader) {
        AgentContext ctx = context(ticketId);
        applyScenarioHeader(ctx, scenarioHeader);
        TurnResultRegistry registry = new TurnResultRegistry();
        BoundTools tools = catalog.toolsFor(ctx, registry);
        String output = tools.execute(name, arguments);
        return new ToolRun(name, tools.label(name).orElse(null), scenarioOf(ctx), output, registry.all());
    }

    @PostMapping("/mock/scenario")
    public ScenarioResponse setScenario(@RequestBody ScenarioRequest request) {
        AgentContext ctx = context(null);
        if (!MockScenarios.exists(request.scenario())) {
            throw new BadRequestException("Unknown scenario '" + request.scenario() + "'; one of "
                    + MockScenarios.ALL);
        }
        mockWorlds().setScenario(ctx, request.scenario());
        return new ScenarioResponse(ctx.userId(), request.scenario());
    }

    @PostMapping("/mock/reset")
    public ScenarioResponse reset() {
        AgentContext ctx = context(null);
        mockWorlds().reset(ctx.userId());
        return new ScenarioResponse(ctx.userId(), mockWorlds().scenarioOf(ctx.userId()));
    }

    /** Calls a write port directly: cancel, createReturn, refund, escalate or note. */
    @PostMapping("/mock/write/{op}")
    public WriteResponse write(@PathVariable String op, @RequestBody WriteRequest req,
                               @RequestParam(required = false) Long ticketId,
                               @RequestHeader(value = SCENARIO_HEADER, required = false) String scenarioHeader) {
        AgentContext ctx = context(ticketId);
        applyScenarioHeader(ctx, scenarioHeader);
        ToolResult<?> result = switch (op) {
            case "cancel" -> direct(() -> orders.cancelOrder(ctx, req.orderId()));
            case "createReturn" -> direct(() -> returns.createReturn(ctx, req.orderId(), req.orderItemId(),
                    req.reason(), req.note()));
            case "refund" -> direct(() -> refunds.issueRefund(ctx, req.orderId(), req.amount(), req.note()));
            case "escalate" -> direct(() -> support.escalate(ctx, req.ticketId(), req.note()));
            case "note" -> direct(() -> support.addNote(ctx, req.ticketId(), req.text()));
            default -> throw new BadRequestException("Unknown op '" + op
                    + "'; one of cancel, createReturn, refund, escalate, note");
        };
        return new WriteResponse(op, scenarioOf(ctx), result);
    }

    private static ToolResult<?> direct(Supplier<?> write) {
        try {
            return ToolResult.ok("direct", write.get());
        } catch (PortException e) {
            return ToolResult.failure(e.code(), e.getMessage());
        }
    }

    /** Built on the request thread from the signed-in user, as the agent's turn will be. */
    private AgentContext context(Long ticketId) {
        return contexts.from(scope.current(), ticketId);
    }

    private void applyScenarioHeader(AgentContext ctx, String scenario) {
        if (scenario == null || scenario.isBlank() || !props.mock().scenarioHeaderEnabled()) return;
        if (!MockScenarios.exists(scenario)) {
            throw new BadRequestException("Unknown scenario '" + scenario + "'; one of " + MockScenarios.ALL);
        }
        mockWorlds().ensureScenario(ctx, scenario);
    }

    private String scenarioOf(AgentContext ctx) {
        MockWorlds w = worlds.getIfAvailable();
        return w == null ? null : w.scenarioOf(ctx.userId());
    }

    private MockWorlds mockWorlds() {
        MockWorlds w = worlds.getIfAvailable();
        if (w == null) throw new BadRequestException("Mock tool backends are not active (profile real-tools)");
        return w;
    }
}
