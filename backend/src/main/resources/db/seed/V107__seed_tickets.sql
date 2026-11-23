-- Support tickets generated from real situations in the seeded data (late/lost parcels, damaged items,
-- refunds, disputes, failed payments, cancellations, account and pre-sale questions).
-- Messages reference the actual order number, product, carrier, tracking number and refund amount.
select setseed(0.107);

-- planner statistics for the tables filled by previous seed steps
analyze;

------------------------------------------------------------------------------------------------
-- conversation scripts: placeholders {first} {agent} {order} {product} {carrier} {tracking} {amount} {date} {seller}
------------------------------------------------------------------------------------------------
create table seed.ticket_script (
    scenario    text    not null,
    step        int     not null,
    author_type text    not null,   -- CUSTOMER / STAFF / SELLER / SYSTEM
    internal    boolean not null default false,
    delay_h     numeric not null,   -- hours after the previous message
    texts       text[]  not null,
    primary key (scenario, step)
);

insert into seed.ticket_script (scenario, step, author_type, internal, delay_h, texts) values
('where_is_order', 1, 'CUSTOMER', false, 0, '{"Hi, my order {order} still hasn''t arrived. The {carrier} tracking ({tracking}) hasn''t updated in days. Can you check what''s going on?","Hello, I ordered the {product} (order {order}) and it''s been a week. Where is my package?","Can you tell me when order {order} will arrive? Tracking number {tracking} just says in transit."}'),
('where_is_order', 2, 'STAFF', false, 3, '{"Hi {first}, thanks for reaching out. I checked with {carrier}: your package for order {order} is in transit and the updated delivery estimate is {date}. I''ll keep an eye on it for you. - {agent}","Hi {first}, sorry for the wait! {carrier} reports a delay at their regional hub. The new estimate for {order} is {date}. If it hasn''t arrived by then, reply here and we''ll make it right. - {agent}"}'),
('where_is_order', 3, 'CUSTOMER', false, 20, '{"It just arrived, thank you!","Thanks, I''ll wait a few more days.","Okay, thanks for checking."}'),
('where_is_order', 4, 'STAFF', false, 6, '{"Glad it arrived, {first}! I''ll close this ticket now. - {agent}","You''re welcome! It should be with you soon; reply here if it doesn''t arrive by then. - {agent}","You''re welcome! It should be with you soon; reply here if it doesn''t arrive by then. - {agent}"}'),

('lost_package', 1, 'CUSTOMER', false, 0, '{"My order {order} has not arrived and {carrier} tracking {tracking} hasn''t moved in over a week. I think it''s lost.","Order {order} never showed up. Tracking has been stuck for 10 days. I need the {product} or my money back."}'),
('lost_package', 2, 'STAFF', false, 4, '{"Hi {first}, I''m sorry about this. I''ve opened a trace with {carrier} for tracking number {tracking}. Carriers usually respond within 2 business days. - {agent}"}'),
('lost_package', 3, 'STAFF', true, 30, '{"Carrier trace came back: package marked as lost in transit. Order total {amount}, above Tier-1 refund limit, escalating to Tier 2 for approval.","Trace confirms loss. Refund of {amount} needs Tier-2 approval per SOP section 4."}'),
('lost_package', 4, 'STAFF', false, 6, '{"Hi {first}, {carrier} has confirmed that your package was lost. I''ve issued a full refund of {amount} to your original payment method. It should appear within 5-10 business days. Sorry again for the trouble! - {agent}"}'),
('lost_package', 5, 'CUSTOMER', false, 10, '{"Thank you for sorting it out.","Thanks, appreciate the quick refund."}'),

('damaged_item', 1, 'CUSTOMER', false, 0, '{"The {product} from order {order} arrived damaged. The box was crushed and the item is broken. What can I do?","Hi, I just received order {order} and the {product} doesn''t work at all. I''d like a replacement or refund.","My {product} arrived with a big crack. Order {order}. Photos attached."}'),
('damaged_item', 2, 'STAFF', false, 5, '{"Hi {first}, I''m so sorry your {product} arrived like that. I''ve opened a return for order {order}. You''ll get a prepaid label by email; once the seller confirms, the refund goes out automatically. - {agent}","Hi {first}, thanks for the photos. That definitely shouldn''t happen. I''ve started a return on your behalf and asked {seller} to prioritize it. - {agent}"}'),
('damaged_item', 3, 'CUSTOMER', false, 26, '{"Thanks, I dropped it off at the post office today.","Great, thank you for the quick help."}'),

('refund_status', 1, 'CUSTOMER', false, 0, '{"I returned the {product} from order {order} but I still haven''t received my refund.","Hi, when will I get my refund for order {order}? The seller received the item last week."}'),
('refund_status', 2, 'STAFF', false, 4, '{"Hi {first}, I checked order {order}: a refund of {amount} was issued on {date}. Banks usually take 5-10 business days to show it on your statement. - {agent}","Hi {first}, good news: your refund of {amount} for order {order} went out on {date}. Depending on your bank it can take up to 10 business days to appear. - {agent}"}'),
('refund_status', 3, 'CUSTOMER', false, 30, '{"Got it, I see it now. Thanks!","Okay, I''ll check again next week."}'),

('wrong_size', 1, 'CUSTOMER', false, 0, '{"The {product} from order {order} doesn''t fit. Can I exchange it for a different size?","I need a different size for the {product} (order {order}). How do exchanges work?"}'),
('wrong_size', 2, 'STAFF', false, 6, '{"Hi {first}, no problem! Exchanges work as a return plus a new order: I''ve opened a return for order {order}, and you can order the new size right away so you don''t have to wait. - {agent}","Hi {first}, sorry it didn''t fit. I''ve started a return for order {order}; once {seller} receives it you''ll be refunded. Tip: the size chart on the listing shows measurements for each size. - {agent}"}'),
('wrong_size', 3, 'CUSTOMER', false, 12, '{"Perfect, thanks!","Thanks for the help."}'),

('disputed_return', 1, 'CUSTOMER', false, 0, '{"The seller rejected my return for order {order} even though the {product} arrived damaged. This is not fair.","I sent back the {product} from order {order} two weeks ago and the seller is refusing to refund me."}'),
('disputed_return', 2, 'STAFF', false, 4, '{"Hi {first}, I''m sorry for the frustration. I''ve escalated the return for order {order} to our disputes team, who will review the evidence from both sides. - {agent}"}'),
('disputed_return', 3, 'SELLER', false, 20, '{"Hello, this is {seller}. We inspected the returned item and it shows signs of use not covered by our return policy.","{seller} here. The item came back without the original accessories, so we cannot issue a full refund."}'),
('disputed_return', 4, 'STAFF', true, 4, '{"Reviewed photos from both parties. Customer opened the return within the window. Policy version at order date applies. Recommend partial refund; needs Tier-2 sign-off.","Seller claims misuse, customer photos show shipping damage. Escalated to T2 supervisor for decision."}'),
('disputed_return', 5, 'CUSTOMER', false, 24, '{"Any update on this? It''s been days.","I still haven''t heard anything back."}'),
('disputed_return', 6, 'STAFF', false, 8, '{"Hi {first}, thanks for your patience. The case for order {order} is with a supervisor and we expect a decision within 2 business days. - {agent}"}'),

('payment_failed', 1, 'CUSTOMER', false, 0, '{"My card was declined when I placed order {order}, but I see a pending charge on my account. Was I charged twice?","Payment for order {order} failed the first time and then went through. Will I be charged twice?"}'),
('payment_failed', 2, 'STAFF', false, 3, '{"Hi {first}, you were only charged once. The first attempt was declined and the pending authorization will drop off automatically within 3-5 business days. - {agent}"}'),
('payment_failed', 3, 'CUSTOMER', false, 15, '{"Great, thanks for explaining.","Okay, thank you."}'),

('cancel_order', 1, 'CUSTOMER', false, 0, '{"Please cancel order {order}, I ordered the wrong item.","Hi, can I cancel order {order}? I found it cheaper elsewhere."}'),
('cancel_order', 2, 'STAFF', false, 1, '{"Hi {first}, done! Order {order} has been cancelled and the payment authorization released. - {agent}","Hi {first}, your order {order} is cancelled. You won''t be charged. - {agent}"}'),

('late_goodwill', 1, 'CUSTOMER', false, 0, '{"My order {order} arrived over a week late. I paid for shipping and expected it much sooner.","Order {order} came very late and I missed the birthday I bought the {product} for."}'),
('late_goodwill', 2, 'STAFF', false, 5, '{"Hi {first}, I''m really sorry about the delay with {carrier}. As an apology I''ve issued a goodwill credit of {amount} to your original payment method. - {agent}"}'),
('late_goodwill', 3, 'CUSTOMER', false, 12, '{"Thank you, that''s appreciated.","Thanks, that helps."}'),

('account', 1, 'CUSTOMER', false, 0, '{"I can''t log in to my account, the password reset email never arrives.","How do I change the email address on my account?","Please remove my old address from my account, I moved.","I''d like to unsubscribe from marketing emails."}'),
('account', 2, 'STAFF', false, 4, '{"Hi {first}, I''ve sent a new reset link and confirmed your email address is correct. Please check your spam folder too. - {agent}","Hi {first}, you can change it under Account > Settings > Email. I''ve sent a confirmation link to the new address for you. - {agent}","Hi {first}, done: the old address has been removed and your new one is set as default. - {agent}","Hi {first}, you''re unsubscribed from marketing emails. You''ll still get order and shipping updates. - {agent}"}'),
('account', 3, 'CUSTOMER', false, 8, '{"That worked, thanks!","Got the link, all sorted.","Perfect, thank you.","Thanks!"}'),

('product_question', 1, 'CUSTOMER', false, 0, '{"Before I buy the {product}: what''s the warranty and is it covered internationally?","Is the {product} sold by {seller} the latest model?","Does the {product} come with everything shown in the photos?"}'),
('product_question', 2, 'STAFF', false, 3, '{"Hi {first}, great question! I''ve forwarded it to {seller}, who knows this product best. You''ll see their answer here. - {agent}"}'),
('product_question', 3, 'SELLER', false, 18, '{"Hi {first}, this is {seller}. Yes, it''s the current model and everything in the photos is included. Warranty details are in the listing.","Hello from {seller}! It ships with all accessories shown and is covered by the manufacturer''s warranty."}'),
('product_question', 4, 'CUSTOMER', false, 10, '{"Thanks, just ordered it!","Great, thank you."}');

-- follow-ups used to build a few very long threads (context-window pressure for later agent work)
create table seed.ticket_followups (
    author_type text   not null,
    texts       text[] not null
);
insert into seed.ticket_followups values
('CUSTOMER', '{"Any update?","I''m still waiting on this.","It''s been another few days, what''s happening?","Can someone please call me?","I''ve now spent hours on this issue.","Is a supervisor looking at this?","I attached more photos.","Please don''t close this ticket, it''s not resolved.","The seller still hasn''t replied to me.","My bank says they have no record of a refund."}'),
('STAFF',    '{"Hi {first}, thanks for following up. The case is still under review. - {agent}","Hi {first}, I''ve added your latest photos to the case. - {agent}","Hi {first}, I''ve chased the seller again today. - {agent}","Hi {first}, I understand your frustration. A supervisor is reviewing it now. - {agent}","Hi {first}, the carrier investigation is still open; I''ll update you as soon as I hear back. - {agent}"}'),
('SELLER',   '{"We''re still investigating on our side.","We''ve asked our warehouse to check the returned parcel again.","We disagree with the claim and have provided our evidence to the platform."}');

------------------------------------------------------------------------------------------------
-- candidate situations
------------------------------------------------------------------------------------------------
create table seed.first_item as
select distinct on (order_id) order_id, id, product_title from order_item order by order_id, id;
alter table seed.first_item add primary key (order_id);
analyze seed.first_item;

create table seed.tk_cand (
    scenario    text,
    customer_id bigint,
    order_id    bigint,
    seller_id   bigint,
    product     text,
    carrier     text,
    tracking    text,
    amount      numeric,
    date_ref    timestamptz,
    created_at  timestamptz,
    category    text,
    base_priority text,
    base_tier   text
);

insert into seed.tk_cand
select 'where_is_order', o.customer_id, o.id, o.seller_id, split_part(i.product_title, ',', 1), s.carrier, s.tracking_number,
       o.total, coalesce(s.delivered_at, s.shipped_at + interval '9 days'), s.shipped_at + interval '5 days', 'SHIPPING', 'NORMAL', 'T1'
from customer_order o
join shipment s on s.order_id = o.id and s.status in ('IN_TRANSIT', 'DELIVERED')
join seed.ot t on t.oid = o.id
join seed.first_item i on i.order_id = o.id
where (t.late or (s.status = 'IN_TRANSIT' and s.shipped_at < (select now_ts from seed.config) - interval '5 days'))
  and random() + o.id * 0 < 0.7;

insert into seed.tk_cand
select 'lost_package', o.customer_id, o.id, o.seller_id, split_part(i.product_title, ',', 1), s.carrier, s.tracking_number,
       o.total, s.shipped_at + interval '15 days', s.shipped_at + interval '11 days', 'SHIPPING',
       case when o.total > 300 then 'URGENT' else 'HIGH' end, 'T2'
from customer_order o
join shipment s on s.order_id = o.id and s.status = 'LOST'
join seed.first_item i on i.order_id = o.id;

insert into seed.tk_cand
select case when rr.reason = 'DOES_NOT_FIT' then 'wrong_size'
            when rr.status = 'DISPUTED' then 'disputed_return'
            else 'damaged_item' end,
       rr.customer_id, o.id, o.seller_id, split_part(i.product_title, ',', 1), null, null,
       i.unit_price * rr.quantity, rr.opened_at,
       rr.opened_at - interval '2 hours' + case when rr.status = 'DISPUTED' then interval '7 days' else interval '0' end,
       'RETURN',
       case when rr.status = 'DISPUTED' then 'HIGH' when rr.reason = 'DOES_NOT_FIT' then 'LOW' else 'HIGH' end,
       case when rr.status = 'DISPUTED' then 'T2' else 'T1' end
from return_request rr
join customer_order o on o.id = rr.order_id
join order_item i on i.id = rr.order_item_id
where (rr.reason in ('DAMAGED', 'DEFECTIVE') and random() + rr.id * 0 < 0.6)
   or (rr.reason = 'DOES_NOT_FIT' and random() + rr.id * 0 < 0.35)
   or rr.status = 'DISPUTED';

insert into seed.tk_cand
select 'refund_status', o.customer_id, o.id, o.seller_id, split_part(i.product_title, ',', 1), null, null,
       rf.amount, rf.created_at, rf.created_at + interval '4 days', 'PAYMENT', 'NORMAL',
       case when rf.amount > 100 then 'T2' else 'T1' end
from refund rf
join return_request rr on rr.id = rf.return_request_id
join customer_order o on o.id = rf.order_id
join order_item i on i.id = rr.order_item_id
where rf.status = 'ISSUED' and rf.created_at + interval '4 days' < (select now_ts from seed.config)
  and random() + rf.id * 0 < 0.3;

insert into seed.tk_cand
select 'late_goodwill', o.customer_id, o.id, o.seller_id, split_part(i.product_title, ',', 1), s.carrier, s.tracking_number,
       rf.amount, rf.created_at, rf.created_at - interval '20 hours', 'SHIPPING', 'NORMAL', 'T1'
from refund rf
join customer_order o on o.id = rf.order_id
join shipment s on s.order_id = o.id
join seed.first_item i on i.order_id = o.id
where rf.reason like 'Goodwill%';

insert into seed.tk_cand
select 'payment_failed', o.customer_id, o.id, o.seller_id, split_part(i.product_title, ',', 1), null, null,
       o.total, o.placed_at, o.placed_at + interval '25 minutes', 'PAYMENT', 'HIGH', 'T1'
from customer_order o
join payment p on p.order_id = o.id and p.status = 'FAILED'
join seed.first_item i on i.order_id = o.id
where random() + o.id * 0 < 0.4;

insert into seed.tk_cand
select 'cancel_order', o.customer_id, o.id, o.seller_id, split_part(i.product_title, ',', 1), null, null,
       o.total, o.placed_at, o.placed_at + interval '50 minutes', 'ORDER', 'NORMAL', 'T1'
from customer_order o
join seed.first_item i on i.order_id = o.id
where o.status = 'CANCELLED' and random() + o.id * 0 < 0.6;

insert into seed.tk_cand
select 'account', m.user_id, null, null, null, null, null, null, null,
       u.created_at + random() * ((select now_ts from seed.config) - u.created_at), 'ACCOUNT', 'NORMAL', 'T1'
from seed.customer_map m join app_user u on u.id = m.user_id
where random() + m.i * 0 < 0.04;

insert into seed.tk_cand
select 'product_question', m.user_id, null, pr.seller_id, split_part(pr.title, ',', 1), null, null, null, null,
       greatest(pr.created_at, u.created_at) + random() * ((select now_ts from seed.config) - greatest(pr.created_at, u.created_at)),
       'PRODUCT', 'LOW', 'T1'
from (select m.user_id, 1 + floor(random() * (select max(id) from product))::bigint as product_id
      from seed.customer_map m where random() < 0.06) m
join app_user u on u.id = m.user_id
join product pr on pr.id = m.product_id
where pr.status = 'ACTIVE';

delete from seed.tk_cand where created_at > (select now_ts from seed.config) - interval '30 minutes';

analyze seed.tk_cand;

-- keep at most ${seed_tickets} tickets, always keeping lost parcels and disputes
create table seed.tk as
select row_number() over (order by c.created_at) as tid, c.*
from (
    select c.*, row_number() over (order by case when c.scenario in ('lost_package', 'disputed_return') then 0 else 1 end,
                                            random()) as pick
    from seed.tk_cand c
) c
where c.pick <= ${seed_tickets};
analyze seed.tk;

------------------------------------------------------------------------------------------------
-- status, assignment
------------------------------------------------------------------------------------------------
create table seed.agents as
select id, role, split_part(full_name, ' ', 1) as first_name,
       row_number() over (partition by role order by id) as rn, count(*) over (partition by role) as cnt
from app_user where role in ('SUPPORT_T1', 'SUPPORT_T2');
analyze seed.agents;

alter table seed.tk add column r_variant float8, add column status text, add column assignee_id bigint, add column agent_name text,
    add column max_step int, add column first_name text, add column seller_name text, add column seller_user_id bigint;

update seed.tk t set
    r_variant = random() + t.tid * 0,
    status = case
        when t.scenario = 'disputed_return' then case when age.d > 60 then 'CLOSED' else 'ESCALATED' end
        when age.d < 1 then 'OPEN'
        when age.d < 3 then 'PENDING'
        when age.d < 30 then 'RESOLVED'
        else 'CLOSED' end,
    first_name = split_part(u.full_name, ' ', 1),
    seller_user_id = (select min(su.id) from app_user su where su.seller_id = t.seller_id)
from (select tid, extract(epoch from (select now_ts from seed.config) - created_at) / 86400 as d from seed.tk) age,
     app_user u
where age.tid = t.tid and u.id = t.customer_id;

update seed.tk t set seller_name = s.name from seller s where s.id = t.seller_id;

update seed.tk t set assignee_id = a.id, agent_name = a.first_name
from seed.agents a
where a.role = case t.base_tier when 'T2' then 'SUPPORT_T2' else 'SUPPORT_T1' end
  and a.rn = 1 + (t.tid % a.cnt)
  and not (t.status = 'OPEN' and t.tid % 3 = 0);

-- open tickets only have the customer's first message; pending ones have the first exchange
update seed.tk t set max_step = case t.status when 'OPEN' then 1 when 'PENDING' then 2 else 99 end;

insert into ticket (id, ticket_number, customer_id, order_id, seller_id, assignee_id, subject, category, status, priority,
                    tier, created_at, updated_at)
select t.tid, 'TCK-' || lpad(t.tid::text, 7, '0'), t.customer_id, t.order_id, t.seller_id, t.assignee_id,
       case t.scenario
           when 'where_is_order' then 'Where is my order ' || o.order_number || '?'
           when 'lost_package' then 'Package lost - order ' || o.order_number
           when 'damaged_item' then 'Damaged/defective item: ' || t.product
           when 'wrong_size' then 'Exchange size for ' || t.product
           when 'disputed_return' then 'Return dispute - order ' || o.order_number
           when 'refund_status' then 'Refund status for order ' || o.order_number
           when 'late_goodwill' then 'Late delivery - order ' || o.order_number
           when 'payment_failed' then 'Double charge? Order ' || o.order_number
           when 'cancel_order' then 'Cancel order ' || o.order_number
           when 'account' then 'Account help'
           else 'Question about ' || t.product end,
       t.category, t.status, t.base_priority,
       case when t.status = 'ESCALATED' then 'T2' else t.base_tier end,
       t.created_at, t.created_at
from seed.tk t left join customer_order o on o.id = t.order_id
order by t.tid;
select setval(pg_get_serial_sequence('ticket', 'id'), (select max(id) from ticket));

------------------------------------------------------------------------------------------------
-- messages
------------------------------------------------------------------------------------------------
create table seed.tm as
select t.tid, sc.step, sc.author_type, sc.internal,
       t.created_at + make_interval(secs => 3600 * sum(sc.delay_h * (0.5 + random() + t.tid * 0))
                                                        over (partition by t.tid order by sc.step)) as created_at,
       seed.render(seed.pick(sc.texts, t.r_variant), jsonb_build_object(
           'first', t.first_name, 'agent', coalesce(t.agent_name, 'AgenticShop Support'),
           'order', coalesce(o.order_number, ''), 'product', coalesce(t.product, 'item'),
           'carrier', coalesce(t.carrier, 'the carrier'), 'tracking', coalesce(t.tracking, ''),
           'amount', coalesce('$' || to_char(t.amount, 'FM999999990.00'), ''),
           'date', coalesce(to_char(t.date_ref, 'Mon DD'), ''),
           'seller', coalesce(t.seller_name, 'the seller'))) as body,
       case sc.author_type when 'CUSTOMER' then t.customer_id when 'STAFF' then t.assignee_id
                           when 'SELLER' then t.seller_user_id end as author_id
from seed.tk t
join seed.ticket_script sc on sc.scenario = t.scenario and sc.step <= t.max_step
left join customer_order o on o.id = t.order_id;
analyze seed.tm;

delete from seed.tm where created_at > (select now_ts from seed.config);

-- a handful of very long escalated threads (~200 messages)
insert into seed.tm (tid, step, author_type, internal, created_at, body, author_id)
select t.tid, 100 + k, a.author_type, false,
       last.created_at + make_interval(hours => k * 5),
       seed.render(seed.pick(f.texts, random() + k * 0 + t.tid * 0),
                   jsonb_build_object('first', t.first_name, 'agent', coalesce(t.agent_name, 'AgenticShop Support'))),
       case a.author_type when 'CUSTOMER' then t.customer_id when 'STAFF' then t.assignee_id else t.seller_user_id end
from (select * from seed.tk where scenario in ('disputed_return', 'lost_package') order by tid limit 6) t
cross join lateral (select max(created_at) as created_at from seed.tm where tid = t.tid) last
cross join generate_series(1, 190) k
cross join lateral (select case when k % 7 = 0 and t.seller_user_id is not null then 'SELLER'
                                when k % 2 = 0 then 'STAFF' else 'CUSTOMER' end as author_type) a
join seed.ticket_followups f on f.author_type = a.author_type
where last.created_at + make_interval(hours => k * 5) < (select now_ts from seed.config);

insert into ticket_message (ticket_id, author_id, author_type, body, internal, created_at)
select tid, author_id, author_type, body, internal, created_at
from seed.tm order by tid, created_at;

update ticket t set updated_at = m.last
from (select ticket_id, max(created_at) as last from ticket_message group by ticket_id) m
where m.ticket_id = t.id;
