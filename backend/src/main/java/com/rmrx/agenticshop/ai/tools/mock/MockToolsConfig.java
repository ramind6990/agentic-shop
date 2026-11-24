package com.rmrx.agenticshop.ai.tools.mock;

import com.rmrx.agenticshop.ai.config.AiProperties;
import com.rmrx.agenticshop.ai.tools.port.CatalogPort;
import com.rmrx.agenticshop.ai.tools.port.OrderPort;
import com.rmrx.agenticshop.ai.tools.port.RefundPort;
import com.rmrx.agenticshop.ai.tools.port.ReturnPort;
import com.rmrx.agenticshop.ai.tools.port.ShipmentPort;
import com.rmrx.agenticshop.ai.tools.port.SupportPort;
import com.rmrx.agenticshop.support.repository.TicketRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The mock port beans, active unless the {@code real-tools} profile is on; real adapters then provide the same
 * port beans and the tools do not change. Every port is wrapped by {@link FailureInjector} when
 * {@code app.ai.failure-injection} is active.
 */
@Slf4j
@Configuration
@Profile("!real-tools")
public class MockToolsConfig {

    private final AiProperties.FailureInjection injection;

    public MockToolsConfig(AiProperties props) {
        this.injection = props.failureInjection();
        log.info("AI tool ports: in-memory mocks (default scenario '{}'), failure injection {}",
                props.mock().defaultScenario(), injection.active() ? injection : "off");
    }

    /**
     * Worlds keyed by subject customer; a staff call in a ticket context finds that ticket's real customer through
     * the support repository (read-only, once per ticket).
     */
    @Bean
    MockWorlds mockWorlds(AiProperties props, TicketRepository tickets, PlatformTransactionManager txManager) {
        TransactionTemplate readOnly = new TransactionTemplate(txManager);
        readOnly.setReadOnly(true);
        return new MockWorlds(props.mock().defaultScenario(), ticketId -> readOnly.execute(
                status -> tickets.findById(ticketId).map(t -> t.getCustomer().getId()).orElse(null)));
    }

    @Bean
    OrderPort orderPort(MockWorlds worlds) {
        return FailureInjector.wrap(OrderPort.class, new MockOrderPort(worlds), injection, worlds);
    }

    @Bean
    ShipmentPort shipmentPort(MockWorlds worlds) {
        return FailureInjector.wrap(ShipmentPort.class, new MockShipmentPort(worlds), injection, worlds);
    }

    @Bean
    CatalogPort catalogPort(MockWorlds worlds) {
        return FailureInjector.wrap(CatalogPort.class, new MockCatalogPort(worlds), injection, worlds);
    }

    @Bean
    ReturnPort returnPort(MockWorlds worlds) {
        return FailureInjector.wrap(ReturnPort.class, new MockReturnPort(worlds), injection, worlds);
    }

    @Bean
    RefundPort refundPort(MockWorlds worlds) {
        return FailureInjector.wrap(RefundPort.class, new MockRefundPort(worlds), injection, worlds);
    }

    @Bean
    SupportPort supportPort(MockWorlds worlds) {
        return FailureInjector.wrap(SupportPort.class, new MockSupportPort(worlds), injection, worlds);
    }
}
