-- Recent orders for the demo customer logins (customer_1..3), so every agent action can be tried on real data:
--   PENDING (payment authorized)     -> cancel, change the delivery address
--   PAID (payment captured)          -> cancel with a refund, change the delivery address
--   SHIPPED, parcel in transit       -> track the parcel; cancelling is refused
--   DELIVERED 3 days ago, 2 units    -> return (1 unit is already REQUESTED: withdraw it, or return the other one)
-- Dates are relative to when this migration runs. Each demo customer also gets a second saved address.

create function pg_temp.demo_order(p_customer bigint, p_product bigint, p_qty int, p_status text,
                                   p_placed timestamptz, p_number text) returns bigint
    language plpgsql as
$f$
declare
    pr      record;
    a       record;
    v_order bigint;
    v_sub   numeric(12, 2);
    v_tax   numeric(12, 2);
    v_ship  numeric(12, 2) := 4.99;
begin
    select id, title, sku, price, currency, seller_id into pr from product where id = p_product;
    select * into a from address where user_id = p_customer order by is_default desc, id limit 1;
    v_sub := pr.price * p_qty;
    v_tax := round(v_sub * 0.08, 2);
    insert into customer_order (order_number, customer_id, seller_id, status, ship_recipient, ship_line1, ship_line2,
                                ship_city, ship_region, ship_postal_code, ship_country, subtotal, shipping_fee, tax,
                                total, currency, placed_at)
    values (p_number, p_customer, pr.seller_id, p_status, a.recipient, a.line1, a.line2, a.city, a.region,
            a.postal_code, a.country, v_sub, v_ship, v_tax, v_sub + v_ship + v_tax, pr.currency, p_placed)
    returning id into v_order;
    insert into order_item (order_id, product_id, product_title, sku, unit_price, quantity, line_total)
    values (v_order, pr.id, pr.title, pr.sku, pr.price, p_qty, v_sub);
    insert into payment (order_id, method, status, amount, provider_ref, created_at)
    values (v_order, 'CARD', case when p_status = 'PENDING' then 'AUTHORIZED' else 'CAPTURED' end,
            v_sub + v_ship + v_tax, 'demo-' || p_number, p_placed);
    if p_status in ('PENDING', 'PAID') then
        update inventory set qty_reserved = qty_reserved + p_qty where product_id = pr.id;
    end if;
    return v_order;
end
$f$;

create function pg_temp.demo_shipment(p_order bigint, p_status text, p_shipped timestamptz,
                                      p_delivered timestamptz, p_tracking text) returns void
    language plpgsql as
$f$
declare
    v_shipment bigint;
    v_dest     text;
begin
    select ship_city || coalesce(', ' || ship_region, '') into v_dest from customer_order where id = p_order;
    insert into shipment (order_id, carrier, tracking_number, status, shipped_at, delivered_at, estimated_delivery)
    values (p_order, 'UPS', p_tracking, p_status, p_shipped, p_delivered,
            coalesce(p_delivered, p_shipped + interval '3 days')::date)
    returning id into v_shipment;
    insert into shipment_event (shipment_id, occurred_at, type, location, description)
    values (v_shipment, p_shipped - interval '5 hours', 'LABEL_CREATED', null,
            'Shipping label created; the seller is preparing the parcel'),
           (v_shipment, p_shipped, 'PICKED_UP', 'Louisville, KY', 'Picked up by the carrier'),
           (v_shipment, p_shipped + interval '14 hours', 'ARRIVED_AT_HUB', 'Chicago, IL',
            'Arrived at the carrier sort facility'),
           (v_shipment, p_shipped + interval '20 hours', 'IN_TRANSIT', 'Chicago, IL',
            'Departed the sort facility, on the way to the destination');
    if p_delivered is not null then
        insert into shipment_event (shipment_id, occurred_at, type, location, description)
        values (v_shipment, p_delivered - interval '6 hours', 'OUT_FOR_DELIVERY', v_dest, 'Out for delivery'),
               (v_shipment, p_delivered, 'DELIVERED', v_dest, 'Delivered to the recipient');
    end if;
end
$f$;

do
$$
declare
    c        record;
    k        int := 0;
    v_day    text := to_char(now(), 'YYMMDD');
    products bigint[];
    v_order  bigint;
begin
    for c in select id, full_name from app_user where username like 'customer\_%' order by username
        loop
            k := k + 1;
            if (select count(*) from address where user_id = c.id) < 2 then
                insert into address (user_id, label, recipient, line1, line2, city, region, postal_code, country,
                                     is_default)
                values (c.id, 'Work', c.full_name, '500 Market Street', 'Floor 4', 'San Francisco', 'CA', '94105',
                        'US', false);
            end if;
            -- four popular products in stock, different for each demo customer
            select array_agg(id) into products
            from (select p.id
                  from product p
                  join inventory i on i.product_id = p.id
                  where p.status = 'ACTIVE' and i.qty_available - i.qty_reserved > 10
                  order by p.sales_count desc, p.id
                  offset (k - 1) * 4 limit 4) top;

            perform pg_temp.demo_order(c.id, products[1], 1, 'PENDING', now() - interval '2 hours',
                                       'AS-' || v_day || '-9' || k || '0001');
            perform pg_temp.demo_order(c.id, products[2], 1, 'PAID', now() - interval '1 day',
                                       'AS-' || v_day || '-9' || k || '0002');

            v_order := pg_temp.demo_order(c.id, products[3], 1, 'SHIPPED', now() - interval '3 days',
                                          'AS-' || v_day || '-9' || k || '0003');
            perform pg_temp.demo_shipment(v_order, 'IN_TRANSIT', now() - interval '1 day', null,
                                          '1ZDEMO' || k || '0003');

            v_order := pg_temp.demo_order(c.id, products[4], 2, 'DELIVERED', now() - interval '8 days',
                                          'AS-' || v_day || '-9' || k || '0004');
            perform pg_temp.demo_shipment(v_order, 'DELIVERED', now() - interval '6 days', now() - interval '3 days',
                                          '1ZDEMO' || k || '0004');
            insert into return_request (order_id, order_item_id, customer_id, quantity, reason, status,
                                        customer_note, opened_at)
            select v_order, oi.id, c.id, 1, 'DOES_NOT_FIT', 'REQUESTED', 'One of the two is too small.',
                   now() - interval '1 day'
            from order_item oi
            where oi.order_id = v_order;
        end loop;
end
$$;
