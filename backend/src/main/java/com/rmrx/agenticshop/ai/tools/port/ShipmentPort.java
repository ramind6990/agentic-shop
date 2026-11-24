package com.rmrx.agenticshop.ai.tools.port;

import com.rmrx.agenticshop.ai.context.AgentContext;
import com.rmrx.agenticshop.ai.contract.tool.ShipmentView;

import java.util.List;

/** Shipments of an order in the caller's scope; an order not yet shipped has none. */
public interface ShipmentPort {

    List<ShipmentView> track(AgentContext ctx, Long orderId);
}
