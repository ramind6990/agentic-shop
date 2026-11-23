-- Seller contracts (+ contract documents), bi-weekly payouts computed from real orders/refunds,
-- and fraud/risk flags derived from behaviour in the data.
select setseed(0.109);

-- planner statistics for the tables filled by previous seed steps
analyze;

------------------------------------------------------------------------------------------------
-- contracts: rates come from the fee schedule version in force when the contract starts;
-- every contract running on 2025-01-01 is renewed on the new schedule.
------------------------------------------------------------------------------------------------
create table seed.fee_schedule (
    version   int, plan text, commission numeric, per_order numeric, monthly numeric,
    valid_from date, valid_to date
);
insert into seed.fee_schedule values
(1, 'STANDARD', 0.15, 0.30, 0, '2021-01-01', '2025-01-01'),
(1, 'PRO', 0.10, 0.30, 39.99, '2021-01-01', '2025-01-01'),
(1, 'ENTERPRISE', 0.08, 0, 299.00, '2021-01-01', '2025-01-01'),
(2, 'STANDARD', 0.15, 0.40, 0, '2025-01-01', null),
(2, 'PRO', 0.11, 0.25, 39.99, '2025-01-01', null),
(2, 'ENTERPRISE', 0.08, 0, 349.00, '2025-01-01', null);

create table seed.contract_plan as
select s.id as seller_id, s.joined_at::date as joined,
       case when pct >= 0.95 then 'ENTERPRISE' when pct >= 0.70 then 'PRO' else 'STANDARD' end as plan,
       random() as r_upgrade, random() as r_upgrade_at
from seller s
join (select seller_id, percent_rank() over (order by size_weight) as pct from seed.seller_map) sz on sz.seller_id = s.id;
analyze seed.contract_plan;

-- segments: (seller, plan, from, to); upgrade STANDARD -> PRO for ~30%; split at the 2025-01-01 renewal
create table seed.contract_seg as
with base as (
    select seller_id, plan, joined as seg_from,
           case when plan = 'STANDARD' and r_upgrade < 0.3
                     and (select now_ts from seed.config)::date - joined > 120
                then joined + (30 + floor(r_upgrade_at * ((select now_ts from seed.config)::date - joined - 60)))::int
           end as upgrade_on
    from seed.contract_plan
),
plans as (
    select seller_id, plan, seg_from, coalesce(upgrade_on, null) as seg_to from base
    union all
    select seller_id, 'PRO', upgrade_on, null from base where upgrade_on is not null
),
split as (
    select seller_id, plan, seg_from, seg_to from plans
    where seg_from >= date '2025-01-01' or (seg_to is not null and seg_to <= date '2025-01-01')
    union all
    select seller_id, plan, seg_from, date '2025-01-01' from plans
    where seg_from < date '2025-01-01' and (seg_to is null or seg_to > date '2025-01-01')
    union all
    select seller_id, plan, date '2025-01-01', seg_to from plans
    where seg_from < date '2025-01-01' and (seg_to is null or seg_to > date '2025-01-01')
)
select row_number() over (partition by seller_id order by seg_from) as version_no, s.*
from split s
where seg_from < (select now_ts from seed.config)::date;
analyze seed.contract_seg;

-- one SELLER_CONTRACT document per seller, one version per contract segment
insert into document (code, type, title, min_role, seller_id)
select 'contract-' || s.slug, 'SELLER_CONTRACT', 'Seller Agreement - ' || s.legal_name, 'FINANCE', s.id
from seller s order by s.id;

insert into document_version (document_id, version_no, effective_from, effective_to, summary)
select d.id, c.version_no, c.seg_from, c.seg_to,
       initcap(c.plan) || ' plan: ' || (fs.commission * 100)::numeric(5, 1) || '% commission, $'
           || to_char(fs.per_order, 'FM0.00') || ' per order, $' || to_char(fs.monthly, 'FM990.00') || ' monthly.'
from seed.contract_seg c
join document d on d.seller_id = c.seller_id and d.type = 'SELLER_CONTRACT'
join seed.fee_schedule fs on fs.plan = c.plan and c.seg_from >= fs.valid_from
                          and (fs.valid_to is null or c.seg_from < fs.valid_to);

insert into document_section (version_id, ordinal, heading, body, min_role)
select v.id, x.ordinal, x.heading, x.body, 'FINANCE'
from document_version v
join document d on d.id = v.document_id and d.type = 'SELLER_CONTRACT'
join seller s on s.id = d.seller_id
cross join lateral (values
    (1, 'Parties', 'This agreement is between AgenticShop Marketplace Inc. ("AgenticShop") and ' || s.legal_name
        || ' ("Seller"), effective ' || to_char(v.effective_from, 'FMMonth DD, YYYY') || '.'),
    (2, 'Fees', v.summary || ' Commission is charged on item subtotals; shipping and taxes are excluded.'),
    (3, 'Payouts', 'AgenticShop pays the Seller every two weeks: item sales plus shipping collected, minus commission and fees, minus refunds issued in the period. Payment is sent 3 business days after the period closes. AgenticShop may hold payouts while a risk review is open.'),
    (4, 'Returns and refunds', 'The Seller accepts returns under the AgenticShop Return & Refund Policy in force on the order date. The Seller bears return shipping for items that arrive damaged, defective, wrong or not as described.'),
    (5, 'Confidentiality', 'The commercial terms of this agreement are confidential and may only be disclosed to the Seller and AgenticShop finance staff.')
) x(ordinal, heading, body);

insert into seller_contract (seller_id, document_id, fee_plan, commission_rate, per_order_fee, monthly_fee,
                             return_window_days, effective_from, effective_to)
select c.seller_id, d.id, c.plan, fs.commission, fs.per_order, fs.monthly, 30, c.seg_from, c.seg_to
from seed.contract_seg c
join document d on d.seller_id = c.seller_id and d.type = 'SELLER_CONTRACT'
join seed.fee_schedule fs on fs.plan = c.plan and c.seg_from >= fs.valid_from
                          and (fs.valid_to is null or c.seg_from < fs.valid_to)
order by c.seller_id, c.seg_from;

------------------------------------------------------------------------------------------------
-- risk flags
------------------------------------------------------------------------------------------------
create table seed.fraud_staff as
select id, row_number() over (order by id) as rn, count(*) over () as cnt from app_user where role = 'FRAUD';
analyze seed.fraud_staff;

-- refund abuse: customers with 3+ return requests
insert into account_flag (subject_type, subject_id, flag_type, risk_score, note, created_by_id, active, created_at)
select 'CUSTOMER', r.customer_id, 'REFUND_ABUSE',
       least(95, 50 + 9 * (r.n - 3) + round((r.n::numeric / o.n) * 40)),
       r.n || ' returns across ' || o.n || ' orders (return rate ' || round(100.0 * r.n / o.n) || '%). Most common reason: '
           || initcap(replace(r.top_reason, '_', ' ')) || '.',
       fs.id, r.third_at > (select now_ts from seed.config) - interval '365 days', r.third_at + interval '1 day'
from (
    select customer_id, count(*) as n, mode() within group (order by reason) as top_reason,
           (array_agg(opened_at order by opened_at))[3] as third_at
    from return_request group by customer_id having count(*) >= 3
) r
join (select customer_id, count(*) as n from customer_order group by customer_id) o on o.customer_id = r.customer_id
join seed.fraud_staff fs on fs.rn = 1 + (r.customer_id % fs.cnt)
where r.third_at + interval '1 day' < (select now_ts from seed.config);

-- chargebacks filed on top of lost-parcel refunds
insert into account_flag (subject_type, subject_id, flag_type, risk_score, note, created_by_id, active, created_at)
select 'CUSTOMER', o.customer_id, 'CHARGEBACK', 60 + floor(random() * 25),
       'Card issuer chargeback (reason: item not received) on order ' || o.order_number
           || ' after a full refund of $' || to_char(o.total, 'FM999990.00') || ' was already issued. Chargeback contested.',
       fs.id, true, s.shipped_at + interval '30 days'
from customer_order o
join shipment s on s.order_id = o.id and s.status = 'LOST'
join seed.fraud_staff fs on fs.rn = 1 + (o.id % fs.cnt)
where random() + o.id * 0 < 0.3 and s.shipped_at + interval '30 days' < (select now_ts from seed.config);

-- account takeover: a few customers who contacted support about account access
insert into account_flag (subject_type, subject_id, flag_type, risk_score, note, created_by_id, active, created_at)
select 'CUSTOMER', t.customer_id, 'ACCOUNT_TAKEOVER', 70 + floor(random() * 20),
       'Password reset, email change and new shipping address within 24 hours; login from a new country. Account locked and owner contacted.',
       fs.id, t.created_at > (select now_ts from seed.config) - interval '90 days', t.created_at + interval '6 hours'
from ticket t
join seed.fraud_staff fs on fs.rn = 1 + (t.id % fs.cnt)
where t.category = 'ACCOUNT' and random() + t.id * 0 < 0.03
  and t.created_at + interval '6 hours' < (select now_ts from seed.config);

-- sellers with high return rates
insert into account_flag (subject_type, subject_id, flag_type, risk_score, note, created_by_id, active, created_at)
select 'SELLER', x.seller_id, 'POLICY_VIOLATION', least(90, 40 + round(x.rate * 200)),
       'Return rate ' || round(x.rate * 100, 1) || '% over ' || x.orders || ' orders; ' || x.nad
           || ' returns marked Not As Described.',
       fs.id, true, (select now_ts from seed.config) - make_interval(days => (x.seller_id % 60)::int)
from (
    select o.seller_id, count(distinct o.id) as orders, count(rr.id)::numeric / count(distinct o.id) as rate,
           count(rr.id) filter (where rr.reason = 'NOT_AS_DESCRIBED') as nad
    from customer_order o left join return_request rr on rr.order_id = o.id
    group by o.seller_id
    having count(distinct o.id) >= 20
) x
join seed.fraud_staff fs on fs.rn = 1 + (x.seller_id % fs.cnt)
where x.rate > 0.11;

-- suspected review manipulation on a few highly-rated sellers
insert into account_flag (subject_type, subject_id, flag_type, risk_score, note, created_by_id, active, created_at)
select 'SELLER', s.id, 'FAKE_REVIEWS', 65 + floor(random() * 25),
       'Cluster of 5-star reviews from accounts created within the same week; under review.',
       fs.id, true, (select now_ts from seed.config) - make_interval(days => (10 + s.id % 50)::int)
from (select id from seller where rating_avg >= 4.3 order by random() limit 6) s
join seed.fraud_staff fs on fs.rn = 1 + (s.id % fs.cnt);

------------------------------------------------------------------------------------------------
-- payouts: bi-weekly periods from the first Monday of history; computed from orders and refunds
------------------------------------------------------------------------------------------------
create table seed.period as
select p::date as period_start, (p + interval '13 days')::date as period_end
from generate_series(date '2023-12-04', (select now_ts from seed.config)::date, interval '14 days') p;
analyze seed.period;

create table seed.payout_calc as
with sales as (
    select o.seller_id, pe.period_start, pe.period_end, count(*) as orders,
           sum(o.subtotal) as items, sum(o.shipping_fee) as shipping
    from customer_order o
    join seed.period pe on o.placed_at::date between pe.period_start and pe.period_end
    where o.status not in ('CANCELLED', 'PENDING')
    group by o.seller_id, pe.period_start, pe.period_end
),
refunds as (
    select o.seller_id, pe.period_start, sum(r.amount) as refunded
    from refund r
    join customer_order o on o.id = r.order_id
    join seed.period pe on r.created_at::date between pe.period_start and pe.period_end
    where r.status = 'ISSUED'
    group by o.seller_id, pe.period_start
)
select s.seller_id, s.period_start, s.period_end, s.orders,
       s.items + s.shipping as gross,
       round(s.items * c.commission_rate, 2) as commission,
       round(s.orders * c.per_order_fee + c.monthly_fee / 2, 2) as fees,
       coalesce(r.refunded, 0) as refunded
from sales s
left join refunds r on r.seller_id = s.seller_id and r.period_start = s.period_start
join lateral (
    select sc.commission_rate, sc.per_order_fee, sc.monthly_fee from seller_contract sc
    where sc.seller_id = s.seller_id and sc.effective_from <= s.period_start
    order by sc.effective_from desc limit 1
) c on true;
analyze seed.payout_calc;

insert into payout (seller_id, period_start, period_end, order_count, gross_sales, commission_amount, fees_amount,
                    refunds_amount, net_amount, status, paid_at)
select p.seller_id, p.period_start, p.period_end, p.orders, p.gross, p.commission, p.fees, p.refunded,
       p.gross - p.commission - p.fees - p.refunded,
       case when hold.flagged and p.period_end > (select now_ts from seed.config)::date - 60 then 'ON_HOLD'
            when p.period_end + 3 < (select now_ts from seed.config)::date then 'PAID'
            else 'PENDING' end,
       case when not (hold.flagged and p.period_end > (select now_ts from seed.config)::date - 60)
                 and p.period_end + 3 < (select now_ts from seed.config)::date
            then (p.period_end + 3)::timestamp at time zone 'UTC' + interval '10 hours' end
from seed.payout_calc p
cross join lateral (
    select exists (select 1 from account_flag f where f.subject_type = 'SELLER' and f.subject_id = p.seller_id and f.active) as flagged
) hold
order by p.period_start, p.seller_id;
