package com.rmrx.agenticshop.app.order.service;

import com.rmrx.agenticshop.app.common.exception.NotFoundException;
import com.rmrx.agenticshop.app.order.entity.CustomerOrder;
import com.rmrx.agenticshop.app.order.repository.CustomerOrderRepository;
import com.rmrx.agenticshop.app.security.AccessScope;
import org.springframework.stereotype.Component;

import java.util.Locale;

/** Loads one order by id or number, for callers allowed to see it; anything else is not found. */
@Component
public class OrderLookup {

    private final CustomerOrderRepository orders;
    private final AccessScope scope;

    public OrderLookup(CustomerOrderRepository orders, AccessScope scope) {
        this.orders = orders;
        this.scope = scope;
    }

    public CustomerOrder require(String idOrNumber) {
        String key = idOrNumber == null ? "" : idOrNumber.trim();
        CustomerOrder o = (!key.isEmpty() && key.chars().allMatch(Character::isDigit)
                ? orders.findDetailedById(Long.parseLong(key))
                : orders.findByOrderNumber(key.toUpperCase(Locale.ROOT)))
                .orElseThrow(() -> new NotFoundException("Order", idOrNumber));
        scope.requireOwned(o.getCustomer().getId(), o.getSeller().getId(), "Order", idOrNumber);
        return o;
    }
}
