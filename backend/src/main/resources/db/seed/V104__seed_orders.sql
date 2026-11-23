-- Orders over ~3 years with seasonality (Black Friday / December peaks), order lines from a single seller,
-- payments and shipments whose statuses are consistent with the timeline relative to "now".
select setseed(0.104);

-- planner statistics for the tables filled by previous seed steps
analyze;

------------------------------------------------------------------------------------------------
-- weighted lookup tables (cumulative distribution in [0,1])
------------------------------------------------------------------------------------------------
create table seed.day as
with bounds as (select history_start as s, (now_ts - interval '1 day')::date as e from seed.config),
days as (
    select d::date as d, b.s, b.e from bounds b, generate_series(b.s, b.e, interval '1 day') d
),
weighted as (
    select d,
           (1 + 0.6 * (d - s)::numeric / greatest(e - s, 1))                       -- business grows over time
           * case
                 when extract(month from d) = 11 and extract(day from d) between 22 and 30 then 2.6  -- Black Friday week
                 when extract(month from d) = 11 then 1.35
                 when extract(month from d) = 12 and extract(day from d) <= 20 then 1.9         -- holiday shopping
                 when extract(month from d) = 12 then 1.1
                 when extract(month from d) = 1 then 0.8
                 when extract(month from d) = 2 then 0.85
                 when extract(month from d) = 7 and extract(day from d) between 8 and 12 then 1.8 -- summer sale
                 else 1 end
           * case extract(isodow from d) when 7 then 1.08 when 1 then 1.05 when 6 then 0.94 else 1 end as w
    from days
)
select d, (sum(w) over (order by d) / sum(w) over ())::float8 as cum from weighted;
analyze seed.day;
create index on seed.day (cum);

-- repeat customers: heavy-tailed purchase frequency
create table seed.cust_w as
select user_id, (sum(w) over (order by user_id) / sum(w) over ())::float8 as cum
from (select m.user_id, 1 / power(random() + 0.03, 1.1) as w from seed.customer_map m) x;
analyze seed.cust_w;
create index on seed.cust_w (cum);

-- products that can be ordered, weighted by popularity
create table seed.prod_w as
select pr.id as product_id, (sum(p.popularity) over (order by pr.id) / sum(p.popularity) over ())::float8 as cum
from product pr join seed.p p on p.pid = pr.id
where pr.status <> 'DRAFT';
analyze seed.prod_w;
create index on seed.prod_w (cum);

create table seed.seller_products as
select seller_id, id as product_id, row_number() over (partition by seller_id order by id) as rn,
       count(*) over (partition by seller_id) as cnt
from product where status <> 'DRAFT';
analyze seed.seller_products;
create index on seed.seller_products (seller_id, rn);

------------------------------------------------------------------------------------------------
-- order skeletons
------------------------------------------------------------------------------------------------
create table seed.o0 as
select k,
       random() as r_day, random() as r_time, random() as r_cust, random() as r_prod,
       random() as r_extra, random() as r_extra1, random() as r_extra2,
       random() as r_cancel, random() as r_ship, random() as r_transit, random() as r_late, random() as r_lost,
       random() as r_carrier, random() as r_pay, random() as r_fail, random() as r_track
from generate_series(1, ${seed_orders}) k;
analyze seed.o0;

create table seed.o1 as
select o.*,
       ((select d.d from seed.day d where d.cum >= o.r_day order by d.cum limit 1)
           + make_interval(secs => 7 * 3600 + power(o.r_time, 0.8) * 16.5 * 3600)) at time zone 'UTC' as placed_at,
       (select c.user_id from seed.cust_w c where c.cum >= o.r_cust order by c.cum limit 1) as customer_id,
       (select pw.product_id from seed.prod_w pw where pw.cum >= o.r_prod order by pw.cum limit 1) as anchor_id
from seed.o0 o;
analyze seed.o1;

-- final order ids are chronological
create table seed.o as
select row_number() over (order by o1.placed_at, o1.k) as oid, o1.*, pr.seller_id,
       a.recipient, a.line1, a.line2, a.city, a.region, a.postal_code, a.country,
       (select cnt from seed.seller_products sp where sp.seller_id = pr.seller_id limit 1) as seller_pc
from seed.o1 o1
join product pr on pr.id = o1.anchor_id
join address a on a.user_id = o1.customer_id and a.is_default;
analyze seed.o;
alter table seed.o add primary key (oid);

------------------------------------------------------------------------------------------------
-- order lines: the anchor product plus up to two more from the same seller
------------------------------------------------------------------------------------------------
create table seed.oi as
select oid, min(line) as line, product_id
from (
    select oid, 1 as line, anchor_id as product_id from seed.o
    union all
    select o.oid, 2, (select sp.product_id from seed.seller_products sp
                      where sp.seller_id = o.seller_id and sp.rn = 1 + floor(o.r_extra1 * o.seller_pc)::int)
    from seed.o o where o.r_extra < 0.33
    union all
    select o.oid, 3, (select sp.product_id from seed.seller_products sp
                      where sp.seller_id = o.seller_id and sp.rn = 1 + floor(o.r_extra2 * o.seller_pc)::int)
    from seed.o o where o.r_extra < 0.10
) x
where product_id is not null
group by oid, product_id;
analyze seed.oi;

alter table seed.oi add column quantity int, add column unit_price numeric(12, 2), add column title text,
    add column sku text, add column oiid bigint;

update seed.oi i set
    quantity = 1 + floor(power(random(), 3) * li.qmax)::int,
    unit_price = pr.price,
    title = pr.title,
    sku = pr.sku
from product pr join seed.leaf_info li on li.leaf_id = pr.category_id
where pr.id = i.product_id;

update seed.oi i set oiid = x.rn
from (select oid, line, row_number() over (order by oid, line) as rn from seed.oi) x
where x.oid = i.oid and x.line = i.line;

------------------------------------------------------------------------------------------------
-- money, timeline and status
------------------------------------------------------------------------------------------------
create table seed.ot as
select o.oid,
       s.subtotal,
       case when s.subtotal >= 35 then 0
            else case o.country when 'US' then 5.99 when 'CA' then 7.99 else 4.99 end end as shipping_fee,
       round(s.subtotal * case o.country
                              when 'GB' then 0.20 when 'DE' then 0.19 when 'CA' then 0.13
                              else case o.region when 'NY' then 0.08875 when 'CA' then 0.0725 when 'TX' then 0.0625
                                                 when 'WA' then 0.065 when 'IL' then 0.0625 when 'FL' then 0.06
                                                 else 0.07 end end, 2) as tax,
       case o.country
           when 'GB' then 'DHL' when 'DE' then 'DHL'
           when 'CA' then seed.wpick('{UPS,FEDEX}', '{60,40}', o.r_carrier)
           else seed.wpick('{USPS,UPS,FEDEX}', '{45,35,20}', o.r_carrier) end as carrier,
       o.placed_at + make_interval(secs => (0.3 + o.r_ship * 1.7) * 86400) as shipped_at
from seed.o o
join (select oid, sum(unit_price * quantity) as subtotal from seed.oi group by oid) s on s.oid = o.oid;
analyze seed.ot;

alter table seed.ot add column delivered_at timestamptz, add column lost boolean, add column status text,
    add column late boolean;

update seed.ot t set
    late = o.r_late < 0.05,
    delivered_at = t.shipped_at + make_interval(secs => 86400 * (
        case t.carrier when 'USPS' then 2 + o.r_transit * 4 when 'UPS' then 1 + o.r_transit * 4
                       when 'FEDEX' then 1 + o.r_transit * 3 else 2 + o.r_transit * 4 end
        + case when o.r_late < 0.05 then 3 + o.r_late * 100 else 0 end)),
    lost = o.r_lost < 0.004 and t.shipped_at < (select now_ts from seed.config) - interval '14 days'
from seed.o o where o.oid = t.oid;

update seed.ot t set status = case
        when o.r_cancel < 0.025 then 'CANCELLED'
        when (select now_ts from seed.config) - o.placed_at < interval '6 hours' and o.r_ship < 0.5 then 'PENDING'
        when t.shipped_at > (select now_ts from seed.config) then 'PAID'
        when t.lost then 'SHIPPED'
        when t.delivered_at > (select now_ts from seed.config) then 'SHIPPED'
        else 'DELIVERED' end
from seed.o o where o.oid = t.oid;

------------------------------------------------------------------------------------------------
-- materialize
------------------------------------------------------------------------------------------------
insert into customer_order (id, order_number, customer_id, seller_id, status, ship_recipient, ship_line1, ship_line2,
                            ship_city, ship_region, ship_postal_code, ship_country, subtotal, shipping_fee, tax,
                            total, currency, placed_at)
select o.oid, 'AS-' || to_char(o.placed_at, 'YYMMDD') || '-' || lpad(o.oid::text, 6, '0'),
       o.customer_id, o.seller_id, t.status, o.recipient, o.line1, o.line2, o.city, o.region, o.postal_code,
       o.country, t.subtotal, t.shipping_fee, t.tax, t.subtotal + t.shipping_fee + t.tax, 'USD', o.placed_at
from seed.o o join seed.ot t on t.oid = o.oid
order by o.oid;
select setval(pg_get_serial_sequence('customer_order', 'id'), (select max(id) from customer_order));

insert into order_item (id, order_id, product_id, product_title, sku, unit_price, quantity, line_total)
select oiid, oid, product_id, title, sku, unit_price, quantity, unit_price * quantity
from seed.oi order by oiid;
select setval(pg_get_serial_sequence('order_item', 'id'), (select max(id) from order_item));

-- failed first attempts (card declined) on ~3% of orders
insert into payment (order_id, method, status, amount, provider_ref, created_at)
select o.oid, 'CARD', 'FAILED', t.subtotal + t.shipping_fee + t.tax,
       'pi_' || substr(md5('fail' || o.oid), 1, 24), o.placed_at - interval '3 minutes'
from seed.o o join seed.ot t on t.oid = o.oid
where o.r_fail < 0.03;

insert into payment (order_id, method, status, amount, provider_ref, created_at)
select o.oid, m.method,
       case t.status when 'PENDING' then 'AUTHORIZED' when 'CANCELLED' then 'VOIDED' else 'CAPTURED' end,
       t.subtotal + t.shipping_fee + t.tax,
       case m.method when 'CARD' then 'pi_' || substr(md5('pay' || o.oid), 1, 24)
                     when 'PAYPAL' then 'PAYID-' || upper(substr(md5('pp' || o.oid), 1, 20))
                     when 'GIFT_CARD' then 'GC-' || upper(substr(md5('gc' || o.oid), 1, 12))
                     else 'SC-' || upper(substr(md5('sc' || o.oid), 1, 12)) end,
       o.placed_at
from seed.o o join seed.ot t on t.oid = o.oid
cross join lateral (select case when o.r_fail < 0.03 then 'CARD'
                                else seed.wpick('{CARD,PAYPAL,GIFT_CARD,STORE_CREDIT}', '{70,20,5,5}', o.r_pay) end as method) m;

insert into shipment (order_id, carrier, tracking_number, status, shipped_at, delivered_at)
select t.oid, t.carrier,
       case t.carrier
           when 'USPS' then '9400 1' || lpad(floor(o.r_track * 1e15)::bigint::text, 17, '0')
           when 'UPS' then '1Z' || upper(substr(md5('ups' || t.oid), 1, 16))
           when 'FEDEX' then lpad(floor(o.r_track * 1e12)::bigint::text, 12, '0')
           else lpad(floor(o.r_track * 1e10)::bigint::text, 10, '0') end,
       case when t.lost then 'LOST' when t.status = 'DELIVERED' then 'DELIVERED' else 'IN_TRANSIT' end,
       t.shipped_at,
       case when t.status = 'DELIVERED' then t.delivered_at end
from seed.ot t join seed.o o on o.oid = t.oid
where t.status in ('SHIPPED', 'DELIVERED');

------------------------------------------------------------------------------------------------
-- make the rest of the data agree with the order history
------------------------------------------------------------------------------------------------
-- a product existed before its first sale
update product pr set created_at = least(pr.created_at, f.first_sale - make_interval(days => 1 + (pr.id % 60)::int)),
                      updated_at = greatest(pr.updated_at, f.first_sale - make_interval(days => 1 + (pr.id % 60)::int))
from (select i.product_id, min(o.placed_at) as first_sale
      from order_item i join customer_order o on o.id = i.order_id group by i.product_id) f
where f.product_id = pr.id;

-- a seller joined before listing their first product
update seller s set joined_at = least(s.joined_at, f.first_listing - interval '14 days')
from (select seller_id, min(created_at) as first_listing from product group by seller_id) f
where f.seller_id = s.id;
update app_user u set created_at = s.joined_at from seller s where u.seller_id = s.id;

-- a customer signed up before their first order
update app_user u set created_at = least(u.created_at, f.first_order - make_interval(days => 1 + (u.id % 200)::int))
from (select customer_id, min(placed_at) as first_order from customer_order group by customer_id) f
where f.customer_id = u.id;

-- units sold
update product pr set sales_count = s.units
from (select i.product_id, sum(i.quantity)::int as units
      from order_item i join customer_order o on o.id = i.order_id
      where o.status <> 'CANCELLED' group by i.product_id) s
where s.product_id = pr.id;
