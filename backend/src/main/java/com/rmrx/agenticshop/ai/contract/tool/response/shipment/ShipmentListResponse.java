package com.rmrx.agenticshop.ai.contract.tool.response.shipment;

import com.rmrx.agenticshop.ai.contract.domain.Carrier;
import com.rmrx.agenticshop.ai.contract.domain.ShipmentStatus;
import com.rmrx.agenticshop.ai.contract.tool.PageInfo;
import com.rmrx.agenticshop.ai.contract.tool.ToolResponse;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** A page of shipments (staff search). */
public record ShipmentListResponse(List<Row> items, PageInfo page) implements ToolResponse {

    public ShipmentListResponse {
        items = List.copyOf(items);
    }

    public record Row(String orderNumber, Long customerId, String customer, String seller, Carrier carrier,
                      String trackingNumber, ShipmentStatus status, Instant shippedAt, LocalDate estimatedDelivery,
                      Instant deliveredAt) implements Serializable {
    }

    @Override
    public Object toAgentSummary() {
        return this;
    }

    @Override
    public List<String> itemIdentifiers() {
        return items.stream().map(Row::trackingNumber).toList();
    }
}
