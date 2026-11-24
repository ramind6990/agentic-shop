package com.rmrx.agenticshop.ai.tools;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rmrx.agenticshop.ai.context.AgentContext;
import com.rmrx.agenticshop.ai.tools.port.CatalogPort;
import com.rmrx.agenticshop.ai.tools.port.OrderPort;
import com.rmrx.agenticshop.ai.tools.port.RefundPort;
import com.rmrx.agenticshop.ai.tools.port.ReturnPort;
import com.rmrx.agenticshop.ai.tools.port.ShipmentPort;
import com.rmrx.agenticshop.ai.tools.port.SupportPort;
import com.rmrx.agenticshop.identity.entity.Role;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.rmrx.agenticshop.ai.tools.CatalogTools.GET_PRODUCT;
import static com.rmrx.agenticshop.ai.tools.CatalogTools.SEARCH_PRODUCTS;
import static com.rmrx.agenticshop.ai.tools.OrderTools.FIND_CUSTOMER_ORDERS;
import static com.rmrx.agenticshop.ai.tools.OrderTools.GET_MY_ORDERS;
import static com.rmrx.agenticshop.ai.tools.OrderTools.GET_ORDER;
import static com.rmrx.agenticshop.ai.tools.OrderTools.TRACK_SHIPMENT;
import static com.rmrx.agenticshop.ai.tools.ReturnTools.GET_RETURN_STATUS;
import static com.rmrx.agenticshop.ai.tools.SupportTools.GET_TICKET_HISTORY;

/**
 * Builds the tools of one turn for one caller: {@link #toolsFor} creates the {@code @Tool} objects with the caller's
 * {@link AgentContext} and the turn's {@link TurnResultRegistry} bound, and keeps only the tools the caller's role
 * may use. The model never sees, and cannot call, any other tool.
 */
@Component
public class ToolCatalog {

    private static final Set<String> STAFF = Set.of(FIND_CUSTOMER_ORDERS, GET_ORDER, TRACK_SHIPMENT,
            SEARCH_PRODUCTS, GET_PRODUCT, GET_RETURN_STATUS, GET_TICKET_HISTORY);

    /** Which tools each role may use. */
    static final Map<Role, Set<String>> ALLOWED = new EnumMap<>(Map.of(
            Role.CUSTOMER, Set.of(GET_MY_ORDERS, GET_ORDER, TRACK_SHIPMENT, SEARCH_PRODUCTS, GET_PRODUCT,
                    GET_RETURN_STATUS, GET_TICKET_HISTORY),
            Role.SELLER, Set.of(GET_ORDER, SEARCH_PRODUCTS, GET_PRODUCT, GET_RETURN_STATUS),
            Role.SUPPORT_T1, STAFF,
            Role.SUPPORT_T2, STAFF,
            Role.ADMIN, STAFF,
            Role.FINANCE, Set.of(GET_ORDER, GET_RETURN_STATUS),
            Role.FRAUD, Set.of(GET_ORDER, GET_RETURN_STATUS)));

    private final OrderPort orders;
    private final ShipmentPort shipments;
    private final CatalogPort catalog;
    private final ReturnPort returns;
    private final RefundPort refunds;
    private final SupportPort support;
    /** Tool results leave out null fields to stay compact for the model. */
    private final JsonMapper json;

    public ToolCatalog(OrderPort orders, ShipmentPort shipments, CatalogPort catalog, ReturnPort returns,
                       RefundPort refunds, SupportPort support, JsonMapper json) {
        this.orders = orders;
        this.shipments = shipments;
        this.catalog = catalog;
        this.returns = returns;
        this.refunds = refunds;
        this.support = support;
        this.json = json.rebuild()
                .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
                .build();
    }

    public static Set<String> allowedTools(Role role) {
        return ALLOWED.getOrDefault(role, Set.of());
    }

    /** The tools for this caller and turn. Build once per turn; the registry collects that turn's results. */
    public BoundTools toolsFor(AgentContext ctx, TurnResultRegistry registry) {
        Set<String> allowed = allowedTools(ctx.role());
        List<Object> candidates = List.of(
                new OrderTools(ctx, registry, json, orders, shipments),
                new CatalogTools(ctx, registry, json, catalog),
                new ReturnTools(ctx, registry, json, returns, refunds),
                new SupportTools(ctx, registry, json, support));

        Map<String, BoundTools.Bound> bound = new LinkedHashMap<>();
        List<Object> instances = new ArrayList<>();
        for (Object instance : candidates) {
            Map<String, ToolSpecification> specs = new LinkedHashMap<>();
            ToolSpecifications.toolSpecificationsFrom(instance).forEach(s -> specs.put(s.name(), s));
            boolean used = false;
            for (Method method : instance.getClass().getDeclaredMethods()) {
                if (!method.isAnnotationPresent(Tool.class)) continue;
                String name = ToolSpecifications.toolNameFrom(method);
                if (!allowed.contains(name)) continue;
                ToolLabel label = method.getAnnotation(ToolLabel.class);
                bound.put(name, new BoundTools.Bound(name, specs.get(name), new DefaultToolExecutor(instance, method),
                        label == null ? "Working on it\u2026" : label.value(), instance));
                used = true;
            }
            if (used) instances.add(instance);
        }
        return new BoundTools(ctx, registry, sorted(bound), List.copyOf(instances), json);
    }

    /** Alphabetical, so the model sees the same tool order every turn. */
    private static Map<String, BoundTools.Bound> sorted(Map<String, BoundTools.Bound> bound) {
        Map<String, BoundTools.Bound> out = new LinkedHashMap<>();
        bound.keySet().stream().sorted().forEach(k -> out.put(k, bound.get(k)));
        return out;
    }
}
