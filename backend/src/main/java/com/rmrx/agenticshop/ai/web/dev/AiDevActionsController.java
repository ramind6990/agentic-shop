package com.rmrx.agenticshop.ai.web.dev;

import com.rmrx.agenticshop.ai.action.ActionNotFoundException;
import com.rmrx.agenticshop.ai.action.ActionService;
import com.rmrx.agenticshop.ai.action.ActionTokenService;
import com.rmrx.agenticshop.ai.action.BlockFactory;
import com.rmrx.agenticshop.ai.action.WriteGateway;
import com.rmrx.agenticshop.ai.context.AgentContext;
import com.rmrx.agenticshop.ai.context.AgentContextFactory;
import com.rmrx.agenticshop.ai.contract.block.Block;
import com.rmrx.agenticshop.ai.contract.response.ItemAction;
import com.rmrx.agenticshop.ai.tools.BoundTools;
import com.rmrx.agenticshop.ai.tools.ToolCatalog;
import com.rmrx.agenticshop.ai.tools.TurnResultRegistry;
import com.rmrx.agenticshop.common.exception.BadRequestException;
import com.rmrx.agenticshop.security.AccessScope;
import com.rmrx.agenticshop.support.service.TicketService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Dev-only access to the action engine (present only with {@code app.ai.dev-endpoints=true}), always as the caller
 * and in a ticket the caller can see: run tools and turn their results into blocks with buttons, propose writes,
 * confirm or decline them by token, and list a ticket's actions with their states. Any unusable token gets the same
 * 404, whatever the reason.
 */
@RestController
@RequestMapping("/api/ai/dev/actions")
@ConditionalOnBooleanProperty("app.ai.dev-endpoints")
public class AiDevActionsController {

    private final ToolCatalog catalog;
    private final BlockFactory blocks;
    private final WriteGateway gateway;
    private final ActionService actions;
    private final ActionTokenService tokens;
    private final AgentContextFactory contexts;
    private final AccessScope scope;
    private final TicketService tickets;
    private final JsonMapper json;

    public AiDevActionsController(ToolCatalog catalog, BlockFactory blocks, WriteGateway gateway,
                                  ActionService actions, ActionTokenService tokens, AgentContextFactory contexts,
                                  AccessScope scope, TicketService tickets, JsonMapper json) {
        this.catalog = catalog;
        this.blocks = blocks;
        this.gateway = gateway;
        this.actions = actions;
        this.tokens = tokens;
        this.contexts = contexts;
        this.scope = scope;
        this.tickets = tickets;
        this.json = json;
    }

    public record Call(String tool, JsonNode args) {
    }

    /** show: an array of resultIds, or the string "all" (also the default). */
    public record BlocksRequest(Long ticketId, List<Call> calls, JsonNode show) {
    }

    public record PrepareRequest(Long ticketId, String tool, JsonNode args) {
    }

    public record CallResult(String tool, String label, JsonNode output) {
    }

    public record BlocksResponse(Long ticketId, List<CallResult> calls, List<Block> blocks,
                                 List<ItemAction> suggestions, List<Long> actionIds, List<String> skipped,
                                 List<com.rmrx.agenticshop.ai.contract.response.Citation> citations) {
    }

    public record TokensRequest(List<String> tokens) {
    }

    public record TtlRequest(Duration itemTtl, Duration confirmationTtl) {
    }

    public record TtlResponse(Duration itemTtl, Duration confirmationTtl) {
    }

    @PostMapping("/blocks")
    public BlocksResponse blocks(@RequestBody BlocksRequest req) {
        AgentContext ctx = context(req.ticketId());
        TurnResultRegistry registry = new TurnResultRegistry();
        List<CallResult> results = runCalls(ctx, registry, req.calls() == null ? List.of() : req.calls());
        BlockFactory.BlockSet set = req.show() == null || req.show().isString()
                && "all".equals(req.show().asString())
                ? blocks.buildAll(registry, ctx)
                : blocks.build(registry, showIds(req.show()), ctx);
        return new BlocksResponse(ctx.ticketId(), results, set.blocks(), set.suggestions(), set.actionIds(),
                set.skipped(), set.citations());
    }

    /** Runs one propose tool as the model would; the confirmation card comes back as a block. */
    @PostMapping("/prepare")
    public BlocksResponse prepare(@RequestBody PrepareRequest req) {
        return blocks(new BlocksRequest(req.ticketId(), List.of(new Call(req.tool(), req.args())), null));
    }

    @PostMapping("/commit/{token}")
    public WriteGateway.CommitResult commit(@PathVariable String token, @RequestParam Long ticketId) {
        return gateway.commit(context(ticketId), token);
    }

    @PostMapping("/decline/{token}")
    public WriteGateway.DeclineResult decline(@PathVariable String token, @RequestParam Long ticketId) {
        return gateway.decline(context(ticketId), token);
    }

    /** All actions of the ticket with their states (overdue ones shown EXPIRED); no tokens or hashes. */
    @GetMapping("/state")
    public List<ActionService.ActionStateView> state(@RequestParam Long ticketId) {
        context(ticketId);
        return actions.byTicket(ticketId);
    }

    /** States of the caller's own tokens in this ticket; foreign or unknown tokens are simply absent. */
    @PostMapping("/state/tokens")
    public Map<String, ActionService.ActionStateView> tokenStates(@RequestParam Long ticketId,
                                                                  @RequestBody TokensRequest req) {
        return actions.stateOf(req.tokens() == null ? List.of() : req.tokens(), context(ticketId));
    }

    /** Dev only: changes the TTL of actions issued from now on, e.g. a 2s confirmation TTL to test expiry. */
    @PostMapping("/ttl")
    public TtlResponse ttl(@RequestBody TtlRequest req) {
        tokens.overrideTtls(req.itemTtl(), req.confirmationTtl());
        return new TtlResponse(tokens.itemTtl(), tokens.confirmationTtl());
    }

    @ExceptionHandler(ActionNotFoundException.class)
    ProblemDetail actionNotFound(ActionNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ActionNotFoundException.MESSAGE);
    }

    private List<CallResult> runCalls(AgentContext ctx, TurnResultRegistry registry, List<Call> calls) {
        BoundTools tools = catalog.toolsFor(ctx, registry);
        List<CallResult> out = new ArrayList<>();
        for (Call call : calls) {
            if (call.tool() == null || call.tool().isBlank()) throw new BadRequestException("tool is required");
            String args = call.args() == null || call.args().isNull() ? "{}" : call.args().toString();
            String output = tools.execute(call.tool(), args);
            out.add(new CallResult(call.tool(), tools.label(call.tool()).orElse(null), json.readTree(output)));
        }
        return out;
    }

    private static List<String> showIds(JsonNode show) {
        if (!show.isArray()) throw new BadRequestException("show must be an array of resultIds or \"all\"");
        List<String> ids = new ArrayList<>();
        show.forEach(n -> ids.add(n.asString()));
        return ids;
    }

    /** The caller's context in a ticket they can see (404 otherwise, as the ticket API). */
    private AgentContext context(Long ticketId) {
        if (ticketId == null) throw new BadRequestException("ticketId is required");
        tickets.get(ticketId);
        return contexts.from(scope.current(), ticketId);
    }
}
