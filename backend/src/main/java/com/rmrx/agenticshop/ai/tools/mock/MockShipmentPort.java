package com.rmrx.agenticshop.ai.tools.mock;

import com.rmrx.agenticshop.ai.context.AgentContext;
import com.rmrx.agenticshop.ai.contract.tool.ShipmentView;
import com.rmrx.agenticshop.ai.tools.port.ShipmentPort;

import java.util.List;

/** {@link ShipmentPort} over the caller's {@link MockWorld}. */
public class MockShipmentPort extends MockSupport implements ShipmentPort {

    public MockShipmentPort(MockWorlds worlds) {
        super(worlds);
    }

    @Override
    public List<ShipmentView> track(AgentContext ctx, Long orderId) {
        return inWorld(ctx, world -> shipments(world, visibleOrder(world, ctx, orderId), ctx));
    }
}
