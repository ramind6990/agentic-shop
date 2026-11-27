package com.rmrx.agenticshop.eval.world;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.rmrx.agenticshop.app.order.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An order. Times are relative to the start of the scenario ({@link WorldTime}: {@code -2h}, {@code -3d}, {@code now}).
 * <p>
 * {@code verdicts} declare what may be done with the order ({@code ok}, or the reason in plain words):
 * {@code cancel}, {@code changeAddress}, {@code return}. A missing verdict has a simple default from the status
 * (cancel / change address while PENDING or PAID, return once DELIVERED); a declared one always wins. The shop's real
 * rules are not re-implemented here.
 */
public class WorldOrder {

    public Long id;
    public String number;
    public OrderStatus status = OrderStatus.PAID;
    public String placed = "-1d";
    /** Username of the customer; default: the actor. */
    public String owner;
    public String seller = "Northwind Outfitters";
    public String currency = "USD";
    public List<WorldItem> items = new ArrayList<>();
    public BigDecimal shippingFee = new BigDecimal("4.99");
    public BigDecimal tax = BigDecimal.ZERO;
    public WorldPayment payment = new WorldPayment();
    public List<WorldShipment> shipments = new ArrayList<>();
    /** Id of the saved address it ships to; default: the owner's default address. */
    public Long shipTo;
    public Map<String, String> verdicts = new LinkedHashMap<>();
    /** Last moment to open a return; default: 30 days after delivery. */
    public String returnDeadline;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public Long ownerId;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public Instant placedAt;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public Instant cancelledAt;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public String cancelReason;

    public BigDecimal subtotal() {
        return items.stream().map(WorldItem::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal total() {
        return subtotal().add(shippingFee).add(tax);
    }
}
