package com.rmrx.agenticshop.ai2.tools.shipment;

import com.rmrx.agenticshop.ai2.contract.domain.Carrier;
import com.rmrx.agenticshop.ai2.contract.domain.ShipmentStatus;
import com.rmrx.agenticshop.ai2.contract.tool.response.shipment.ShipmentListResponse;
import com.rmrx.agenticshop.ai2.contract.tool.response.shipment.TrackingResponse;
import com.rmrx.agenticshop.ai2.tools.AgentToolSet;
import com.rmrx.agenticshop.ai2.tools.Audience;
import com.rmrx.agenticshop.ai2.tools.ToolAudience;
import com.rmrx.agenticshop.ai2.tools.ToolDates;
import com.rmrx.agenticshop.ai2.tools.ToolLabel;
import com.rmrx.agenticshop.ai2.tools.ToolPaging;
import com.rmrx.agenticshop.app.common.dto.PageResponse;
import com.rmrx.agenticshop.app.order.dto.OrderDtos.ShipmentListItem;
import com.rmrx.agenticshop.app.order.service.ShipmentSearch;
import com.rmrx.agenticshop.app.order.service.ShipmentService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;

/** Parcel tracking for an order, and a shipment search across customers for staff. */
@Component
public class ShipmentTools implements AgentToolSet {

    public static final String TRACK_SHIPMENT = "track_shipment";
    public static final String SEARCH_SHIPMENTS = "search_shipments";

    private final ShipmentService shipments;
    private final ShipmentToolMapper mapper;

    public ShipmentTools(ShipmentService shipments, ShipmentToolMapper mapper) {
        this.shipments = shipments;
        this.mapper = mapper;
    }

    @ToolAudience({Audience.CUSTOMER, Audience.STAFF})
    @ToolLabel("Tracking the parcel")
    @Tool(name = TRACK_SHIPMENT, value = "Where an order's parcels are: carrier, tracking number, status, estimated "
            + "delivery and the latest carrier scans (time, place, what happened). No shipments means the order has "
            + "not been handed to a carrier yet. Give exactly one of orderNumber or trackingNumber.")
    public TrackingResponse trackShipment(
            @P(value = "The order number", required = false) String orderNumber,
            @P(value = "A carrier tracking number the user gave", required = false) String trackingNumber) {
        return mapper.toTracking(shipments.track(blankToNull(orderNumber), blankToNull(trackingNumber)));
    }

    @ToolAudience(Audience.STAFF)
    @ToolLabel("Searching shipments")
    @Tool(name = SEARCH_SHIPMENTS, value = "Find shipments across customers, most recently shipped first: a page of 10 "
            + "and the total. overdueOnly finds parcels still in transit after their estimated delivery date.")
    public ShipmentListResponse searchShipments(
            @P(value = "Only this carrier", required = false) Carrier carrier,
            @P(value = "Only these statuses", required = false) List<ShipmentStatus> statuses,
            @P(value = "Shipped on or after this date (yyyy-MM-dd)", required = false) String shippedFrom,
            @P(value = "Shipped on or before this date (yyyy-MM-dd)", required = false) String shippedTo,
            @P(value = "Only this customer's shipments", required = false) Long customerId,
            @P(value = "Only this seller's shipments", required = false) Long sellerId,
            @P(value = "True for parcels in transit past their estimated delivery", required = false)
            Boolean overdueOnly,
            @P(value = "Page 1 to 5; only when the user asks to see more", required = false) Integer page) {
        ShipmentSearch search = new ShipmentSearch(mapper.toDomain(carrier), mapper.toDomain(statuses),
                ToolDates.from(shippedFrom, "shippedFrom"), ToolDates.until(shippedTo, "shippedTo"), customerId,
                sellerId, Boolean.TRUE.equals(overdueOnly));
        PageResponse<ShipmentListItem> found = shipments.search(search, ToolPaging.pageable(page,
                Sort.by(Sort.Order.desc("shippedAt"), Sort.Order.desc("id"))));
        return new ShipmentListResponse(found.content().stream().map(mapper::toRow).toList(), ToolPaging.info(found));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
