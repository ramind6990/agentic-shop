package com.rmrx.agenticshop.eval.world;

import com.rmrx.agenticshop.app.order.entity.Carrier;
import com.rmrx.agenticshop.app.order.entity.ShipmentStatus;

import java.util.ArrayList;
import java.util.List;

/** A parcel of an order with its carrier scans; {@code eta} is a relative day ({@code +2d}). */
public class WorldShipment {

    public Long id;
    public Carrier carrier = Carrier.UPS;
    public String trackingNumber;
    public ShipmentStatus status = ShipmentStatus.IN_TRANSIT;
    public String shipped;
    public String delivered;
    public String eta;
    public List<WorldScan> events = new ArrayList<>();
}
