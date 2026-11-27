package com.rmrx.agenticshop.app.order.service;

import com.rmrx.agenticshop.app.order.entity.CustomerOrder;
import com.rmrx.agenticshop.app.order.repository.ShipmentRepository;
import com.rmrx.agenticshop.app.returns.repository.ReturnRequestRepository;
import com.rmrx.agenticshop.app.seller.entity.SellerContract;
import com.rmrx.agenticshop.app.seller.repository.SellerContractRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;

/** Gathers the {@link OrderFacts} of an order. */
@Component
public class OrderFactsLoader {

    /** Return window when the seller has no contract in force on the order date. */
    static final int DEFAULT_RETURN_WINDOW_DAYS = 30;

    private final ShipmentRepository shipments;
    private final ReturnRequestRepository returns;
    private final SellerContractRepository contracts;

    public OrderFactsLoader(ShipmentRepository shipments, ReturnRequestRepository returns,
                            SellerContractRepository contracts) {
        this.shipments = shipments;
        this.returns = returns;
        this.contracts = contracts;
    }

    public OrderFacts load(CustomerOrder order) {
        return new OrderFacts(order, shipments.findByOrderIdOrderByIdAsc(order.getId()),
                returns.findByOrderIdOrderByOpenedAtAsc(order.getId()), returnWindowDays(order));
    }

    /** The window of the seller contract in force on the order date. */
    private int returnWindowDays(CustomerOrder order) {
        LocalDate placed = order.getPlacedAt().atZone(ZoneOffset.UTC).toLocalDate();
        return contracts.findBySellerIdOrderByEffectiveFromDesc(order.getSeller().getId()).stream()
                .filter(c -> !c.getEffectiveFrom().isAfter(placed))
                .findFirst()
                .map(SellerContract::getReturnWindowDays)
                .orElse(DEFAULT_RETURN_WINDOW_DAYS);
    }
}
