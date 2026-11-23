-- Returns and refunds. Return windows and fees follow the return-policy version in force at the order date
-- (the policy text itself is seeded in V107):
--   v1 (until 2024-08-31): 30 days, free returns, full refund
--   v2 (2024-09-01 .. 2025-09-30): 14 days; change-of-mind returns pay $6.99 return shipping,
--                                  electronics change-of-mind returns pay a 15% restocking fee
--   v3 (from 2025-10-01): 30 days (electronics 15 days); electronics change-of-mind returns pay 10% restocking
select setseed(0.105);

-- planner statistics for the tables filled by previous seed steps
analyze;

create table seed.return_notes (
    reason text   primary key,
    notes  text[] not null
);
insert into seed.return_notes values
('DOES_NOT_FIT',     '{"Ordered my usual size but it runs small.","Too big in the shoulders, I need one size down.","The length is much shorter than the size chart says.","Fits tight around the waist, would like to exchange for a larger size."}'),
('CHANGED_MIND',     '{"Found a better option elsewhere, item is unopened.","Bought it as a gift and the recipient already has one.","Ordered two by mistake.","Not what I need after all, still in original packaging."}'),
('NOT_AS_DESCRIBED', '{"The color is very different from the photos.","Listing says stainless steel but it looks like coated aluminum.","Specs on the box do not match the listing.","Material feels much cheaper than described."}'),
('DAMAGED',          '{"Box was crushed and the item is cracked.","Arrived with a dent on the side.","The package was soaked and the item is damaged.","Glass part was shattered on arrival."}'),
('WRONG_ITEM',       '{"Received a different model than I ordered.","Wrong color was sent.","Got the wrong size even though the order says otherwise.","This is a completely different product."}'),
('DEFECTIVE',        '{"Stopped working after two days.","Does not turn on at all.","Battery will not hold a charge.","Makes a loud rattling noise when in use."}'),
('ARRIVED_LATE',     '{"Arrived a week after the event I needed it for.","Delivery took far longer than promised, no longer needed.","It came too late to be used as a gift."}');

-- candidate returns: one line of a delivered order
create table seed.ret as
select o.id as order_id, o.customer_id, o.placed_at, o.status, s.delivered_at, li.root, ot.late,
       i.id as order_item_id, i.product_id, i.unit_price, i.quantity, p.quality,
       random() as r_pick, random() as r_reason, random() as r_open, random() as r_status, random() as r_note,
       random() as r_qty, random() as r_resolve, random() as r_staff
from customer_order o
join shipment s on s.order_id = o.id and s.status = 'DELIVERED'
join seed.ot ot on ot.oid = o.id
join lateral (select * from order_item i where i.order_id = o.id order by random() + o.id * 0 limit 1) i on true
join product pr on pr.id = i.product_id
join seed.leaf_info li on li.leaf_id = pr.category_id
join seed.p p on p.pid = pr.id
where s.delivered_at < (select now_ts from seed.config) - interval '1 day';
analyze seed.ret;

delete from seed.ret r
where r.r_pick >= case r.root when 'fashion' then 0.14 when 'electronics' then 0.07 else 0.04 end
                   * (1 + (5 - r.quality) / 2.5)
                   * case when r.late then 2 else 1 end;

alter table seed.ret add column reason text, add column policy int, add column window_days int,
    add column opened_at timestamptz, add column ret_status text, add column resolved_at timestamptz,
    add column late_request boolean, add column ret_qty int;

update seed.ret r set
    reason = case
        when r.late and r.r_reason < 0.5 then 'ARRIVED_LATE'
        when r.root = 'fashion' then seed.wpick('{DOES_NOT_FIT,CHANGED_MIND,NOT_AS_DESCRIBED,DAMAGED,WRONG_ITEM,DEFECTIVE}', '{45,15,17,8,8,7}', r.r_reason)
        when r.root = 'electronics' then seed.wpick('{DEFECTIVE,DAMAGED,NOT_AS_DESCRIBED,CHANGED_MIND,WRONG_ITEM}', '{32,15,20,23,10}', r.r_reason)
        else seed.wpick('{DAMAGED,NOT_AS_DESCRIBED,CHANGED_MIND,WRONG_ITEM,DEFECTIVE}', '{27,20,28,10,15}', r.r_reason) end,
    policy = case when r.placed_at < timestamptz '2024-09-01' then 1
                  when r.placed_at < timestamptz '2025-10-01' then 2 else 3 end,
    ret_qty = case when r.quantity > 1 and r.r_qty < 0.5 then 1 else r.quantity end;

update seed.ret r set window_days = case r.policy when 1 then 30 when 2 then 14
                                                  else case when r.root = 'electronics' then 15 else 30 end end;

-- most requests come early in the window; ~2% arrive after it closed
update seed.ret r set
    late_request = r.r_open > 0.98,
    opened_at = r.delivered_at + make_interval(secs => 86400 * case
        when r.r_open > 0.98 then r.window_days + 2 + (r.r_open - 0.98) * 400
        else 0.2 + r.window_days * power(r.r_open / 0.98, 1.6) end);

delete from seed.ret where opened_at > (select now_ts from seed.config) - interval '2 hours';

update seed.ret r set ret_status = case
        when r.late_request then 'REJECTED'
        when age.d < 3 then 'REQUESTED'
        when age.d < 7 then case when r.r_status < 0.9 then 'APPROVED' else 'REJECTED' end
        when age.d < 12 then 'RECEIVED'
        when r.r_status < 0.80 then 'CLOSED'
        when r.r_status < 0.93 then 'REJECTED'
        when age.d < 90 then 'DISPUTED'
        else 'CLOSED' end
from (select order_item_id, extract(epoch from (select now_ts from seed.config) - opened_at) / 86400 as d from seed.ret) age
where age.order_item_id = r.order_item_id;

update seed.ret r set resolved_at = case when r.ret_status in ('CLOSED', 'REJECTED')
    then least(r.opened_at + make_interval(secs => 86400 * (2 + r.r_resolve * 10)),
               (select now_ts from seed.config) - interval '1 hour') end;

insert into return_request (order_id, order_item_id, customer_id, quantity, reason, status, customer_note, seller_note,
                            opened_at, resolved_at)
select r.order_id, r.order_item_id, r.customer_id, r.ret_qty, r.reason, r.ret_status,
       seed.pick(n.notes, r.r_note),
       case
           when r.late_request then 'Return requested ' || (extract(day from r.opened_at - r.delivered_at))::int
                                    || ' days after delivery; the ' || r.window_days || '-day return window has closed.'
           when r.ret_status = 'REJECTED' then seed.pick('{"Item was returned used and without original packaging.","Photos show damage consistent with misuse, not shipping.","Returned item does not match the serial number we shipped."}'::text[], r.r_note)
           when r.ret_status = 'DISPUTED' then seed.pick('{"We believe the item was damaged after delivery; requesting platform review.","Customer kept accessories that were part of the bundle.","Tracking shows the return was never dropped off."}'::text[], r.r_note)
           when r.ret_status = 'CLOSED' and r.reason in ('DAMAGED', 'DEFECTIVE', 'WRONG_ITEM') then 'Sorry about that! Refund approved, no need to return the damaged item.'
       end,
       r.opened_at, r.resolved_at
from seed.ret r join seed.return_notes n on n.reason = r.reason
order by r.opened_at;

------------------------------------------------------------------------------------------------
-- refunds
------------------------------------------------------------------------------------------------
create table seed.staff as
select id, role, row_number() over (partition by role order by id) as rn, count(*) over (partition by role) as cnt
from app_user where role in ('SUPPORT_T1', 'SUPPORT_T2');
analyze seed.staff;

create function seed.staff_for(p_amount numeric, r double precision) returns bigint
    language sql stable as
$$
select id from seed.staff
where role = case when p_amount <= 100 then 'SUPPORT_T1' else 'SUPPORT_T2' end
  and rn = 1 + floor(r * cnt)::int
$$;

-- 1) refunds for completed returns (and half of received ones), minus fees where the policy applies
insert into refund (order_id, return_request_id, amount, reason, status, issued_by_id, idempotency_key, created_at)
select rr.order_id, rr.id, amt.v,
       'Return: ' || initcap(replace(rr.reason, '_', ' ')) || ' (policy v' || r.policy || ')'
           || case when amt.fee > 0 then ', fees deducted $' || amt.fee else '' end,
       case when r.r_staff < 0.005 then 'FAILED' else 'ISSUED' end,
       seed.staff_for(amt.v, r.r_staff), null,
       coalesce(rr.resolved_at, rr.opened_at + interval '8 days')
from return_request rr
join seed.ret r on r.order_item_id = rr.order_item_id
cross join lateral (
    select r.unit_price * rr.quantity as gross,
           case
               when r.policy = 2 and rr.reason in ('CHANGED_MIND', 'DOES_NOT_FIT')
                   then 6.99 + case when r.root = 'electronics' then round(r.unit_price * rr.quantity * 0.15, 2) else 0 end
               when r.policy = 3 and r.root = 'electronics' and rr.reason = 'CHANGED_MIND'
                   then round(r.unit_price * rr.quantity * 0.10, 2)
               else 0 end as fee
) f
cross join lateral (select greatest(f.gross - f.fee, 0) as v, f.fee) amt
where rr.status = 'CLOSED' or (rr.status = 'RECEIVED' and r.r_resolve < 0.5);

-- 2) lost packages: full refund ~15 days after shipping
insert into refund (order_id, return_request_id, amount, reason, status, issued_by_id, idempotency_key, created_at)
select o.id, null, o.total, 'Package lost in transit (' || s.carrier || ' ' || s.tracking_number || ')', 'ISSUED',
       seed.staff_for(o.total, random() + o.id * 0), null, s.shipped_at + interval '15 days'
from customer_order o join shipment s on s.order_id = o.id and s.status = 'LOST';

-- 3) goodwill credits for ~30% of late deliveries
insert into refund (order_id, return_request_id, amount, reason, status, issued_by_id, idempotency_key, created_at)
select o.id, null, greatest(o.shipping_fee, 5.00), 'Goodwill credit: late delivery', 'ISSUED',
       seed.staff_for(5, random() + o.id * 0), null, s.delivered_at + interval '1 day'
from customer_order o
join shipment s on s.order_id = o.id and s.status = 'DELIVERED'
join seed.ot t on t.oid = o.id
where t.late and random() + o.id * 0 < 0.3
  and s.delivered_at + interval '1 day' < (select now_ts from seed.config);

------------------------------------------------------------------------------------------------
-- order and payment statuses reflect refunds
------------------------------------------------------------------------------------------------
with totals as (
    select order_id, sum(amount) as refunded from refund where status = 'ISSUED' group by order_id
)
update customer_order o set status = case
        when exists (select 1 from shipment s where s.order_id = o.id and s.status = 'LOST') then 'REFUNDED'
        when t.refunded >= o.subtotal then 'RETURNED'
        else 'PARTIALLY_REFUNDED' end
from totals t where t.order_id = o.id;

with totals as (
    select order_id, sum(amount) as refunded from refund where status = 'ISSUED' group by order_id
)
update payment p set status = case when t.refunded >= p.amount then 'REFUNDED' else 'PARTIALLY_REFUNDED' end
from totals t where t.order_id = p.order_id and p.status = 'CAPTURED';
