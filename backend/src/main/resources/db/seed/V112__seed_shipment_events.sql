-- Tracking data for the seeded shipments: an estimated delivery date and the carrier scans of each parcel.
-- Scans are spread over the shipment's journey: label, pick-up and a hub for everyone; then departure, out for
-- delivery and delivery for delivered parcels, a departure for parcels still in transit (only scans already in the
-- past), and a delivery exception for lost ones. Hubs are real sort centers of each carrier.

update shipment
set estimated_delivery = (shipped_at + make_interval(days => 2 + (id % 5)::int))::date
where shipped_at is not null
  and estimated_delivery is null;

with journey as (
    select s.id,
           s.status,
           s.shipped_at,
           case s.status
               when 'DELIVERED' then s.delivered_at
               when 'LOST' then s.shipped_at + interval '4 days'
               else (s.estimated_delivery + time '18:00') at time zone 'UTC'
           end as arrives_at,
           o.ship_city || coalesce(', ' || o.ship_region, '') as destination,
           case s.carrier
               when 'UPS' then array ['Louisville, KY', 'Chicago, IL', 'Dallas, TX', 'Atlanta, GA', 'Ontario, CA']
               when 'FEDEX' then array ['Memphis, TN', 'Indianapolis, IN', 'Newark, NJ', 'Oakland, CA', 'Fort Worth, TX']
               when 'DHL' then array ['Cincinnati, OH', 'Los Angeles, CA', 'Miami, FL', 'New York, NY', 'Chicago, IL']
               else array ['Denver, CO', 'Philadelphia, PA', 'Seattle, WA', 'Phoenix, AZ', 'Kansas City, MO']
           end as hubs
    from shipment s
    join customer_order o on o.id = s.order_id
    where s.shipped_at is not null
      and not exists (select 1 from shipment_event e where e.shipment_id = s.id)
)
insert into shipment_event (shipment_id, occurred_at, type, location, description)
select j.id, ev.occurred_at, ev.type, ev.location, ev.description
from journey j
cross join lateral (values
    (j.shipped_at - interval '5 hours', 'LABEL_CREATED', null,
     'Shipping label created; the seller is preparing the parcel', true),
    (j.shipped_at, 'PICKED_UP', j.hubs[1 + j.id % 5],
     'Picked up by the carrier', true),
    (j.shipped_at + (j.arrives_at - j.shipped_at) * 0.35, 'ARRIVED_AT_HUB', j.hubs[1 + (j.id + 2) % 5],
     'Arrived at the carrier sort facility', true),
    (j.shipped_at + (j.arrives_at - j.shipped_at) * 0.55, 'IN_TRANSIT', j.hubs[1 + (j.id + 2) % 5],
     'Departed the sort facility, on the way to the destination', j.status in ('DELIVERED', 'IN_TRANSIT')),
    (greatest(j.shipped_at + (j.arrives_at - j.shipped_at) * 0.6, j.arrives_at - interval '6 hours'),
     'OUT_FOR_DELIVERY', j.destination, 'Out for delivery', j.status = 'DELIVERED'),
    (j.arrives_at, 'DELIVERED', j.destination, 'Delivered to the recipient', j.status = 'DELIVERED'),
    (j.arrives_at, 'DELIVERY_EXCEPTION', j.hubs[1 + (j.id + 2) % 5],
     'The parcel could not be located; the carrier opened an investigation', j.status = 'LOST')
) as ev(occurred_at, type, location, description, applies)
where ev.applies
  and ev.occurred_at <= now();
