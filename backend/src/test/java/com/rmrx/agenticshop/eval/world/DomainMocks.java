package com.rmrx.agenticshop.eval.world;

import com.rmrx.agenticshop.app.catalog.service.CategoryService;
import com.rmrx.agenticshop.app.catalog.service.ProductSearchCriteriaParser;
import com.rmrx.agenticshop.app.catalog.service.ProductService;
import com.rmrx.agenticshop.app.identity.service.AddressService;
import com.rmrx.agenticshop.app.identity.service.UserService;
import com.rmrx.agenticshop.app.order.service.OrderCommandService;
import com.rmrx.agenticshop.app.order.service.OrderService;
import com.rmrx.agenticshop.app.order.service.ShipmentService;
import com.rmrx.agenticshop.app.returns.service.RefundApprovalService;
import com.rmrx.agenticshop.app.returns.service.RefundCommandService;
import com.rmrx.agenticshop.app.returns.service.ReturnCommandService;
import com.rmrx.agenticshop.app.returns.service.ReturnService;
import com.rmrx.agenticshop.app.support.service.TicketCommandService;
import com.rmrx.agenticshop.app.support.service.TicketService;
import org.mockito.Mockito;
import org.mockito.invocation.Invocation;
import org.springframework.context.ApplicationContext;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The mocked domain services (the {@code @MockitoBean}s of the eval test) and their wiring to a world: every service a
 * tool or a precheck calls is a mock, so no run ever touches real shop data. Before each scenario the mocks are reset
 * and the stubs of each family are installed; afterwards {@link #unstubbedCalls()} lists calls nothing answered (they
 * returned null, so the run's result is not trustworthy).
 */
public final class DomainMocks {

    /** Every mocked type; the eval test declares exactly these as {@code @MockitoBean(types = …)}. */
    public static final List<Class<?>> TYPES = List.of(ProductService.class, CategoryService.class,
            ProductSearchCriteriaParser.class, OrderService.class, OrderCommandService.class, ShipmentService.class,
            ReturnService.class, ReturnCommandService.class, RefundCommandService.class, RefundApprovalService.class,
            AddressService.class, UserService.class, TicketService.class, TicketCommandService.class);

    private final ApplicationContext context;
    private final List<Object> mocks;

    public DomainMocks(ApplicationContext context) {
        this.context = context;
        this.mocks = TYPES.stream().<Object>map(context::getBean).toList();
        mocks.forEach(m -> {
            if (!Mockito.mockingDetails(m).isMock()) {
                throw new IllegalStateException(m.getClass().getName() + " is not a mock: declare it in @MockitoBean");
            }
        });
    }

    /** Resets every mock and answers from this world. */
    public void install(WorldContext world) {
        Mockito.reset(mocks.toArray());
        new OrderStubs(world).install(bean(OrderService.class), bean(OrderCommandService.class));
        new ShipmentStubs(world).install(bean(ShipmentService.class));
        new AddressStubs(world).install(bean(AddressService.class));
        new TicketStubs(world).install(bean(TicketService.class), bean(TicketCommandService.class));
    }

    /** Calls since the last {@link #install} that no stub answered, e.g. {@code ProductService.search(…)}. */
    public List<String> unstubbedCalls() {
        return mocks.stream()
                .flatMap(m -> Mockito.mockingDetails(m).getInvocations().stream())
                .filter(i -> i.stubInfo() == null)
                .filter(i -> i.getMethod().getDeclaringClass() != Object.class)
                .map(DomainMocks::describe)
                .toList();
    }

    private <T> T bean(Class<T> type) {
        return context.getBean(type);
    }

    private static String describe(Invocation i) {
        String args = Arrays.stream(i.getArguments()).map(String::valueOf).collect(Collectors.joining(", "));
        String type = Mockito.mockingDetails(i.getMock()).getMockCreationSettings().getTypeToMock().getSimpleName();
        return type + "." + i.getMethod().getName() + "(" + args + ")";
    }
}
