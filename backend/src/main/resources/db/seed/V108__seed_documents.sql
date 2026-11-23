-- Document corpora: versioned public policies, the support SOP (with Tier-2-only sections),
-- the fraud playbook and the seller fee schedule. Seller contracts are generated in V109.

insert into document (code, type, title, min_role, seller_id) values
('return-policy',       'PUBLIC_POLICY',  'Return & Refund Policy',            'CUSTOMER',   null),
('shipping-policy',     'PUBLIC_POLICY',  'Shipping Policy',                   'CUSTOMER',   null),
('warranty-policy',     'PUBLIC_POLICY',  'Warranty Policy',                   'CUSTOMER',   null),
('seller-fee-schedule', 'PUBLIC_POLICY',  'Seller Fee Schedule',               'SELLER',     null),
('support-sop',         'SUPPORT_SOP',    'Customer Support Standard Operating Procedures', 'SUPPORT_T1', null),
('fraud-playbook',      'FRAUD_PLAYBOOK', 'Fraud & Risk Playbook',             'FRAUD',      null);

insert into document_version (document_id, version_no, effective_from, effective_to, summary)
select d.id, v.version_no, v.effective_from, v.effective_to, v.summary
from (values
    ('return-policy', 1, date '2023-01-01', date '2024-09-01', '30-day returns on all items, free return shipping, full refunds.'),
    ('return-policy', 2, date '2024-09-01', date '2025-10-01', 'Return window shortened to 14 days. Change-of-mind returns pay $6.99 return shipping; electronics change-of-mind returns pay a 15% restocking fee.'),
    ('return-policy', 3, date '2025-10-01', null,             '30-day window restored (15 days for electronics). Free return shipping again; 10% restocking fee only for change-of-mind electronics returns.'),
    ('shipping-policy', 1, date '2023-01-01', date '2025-03-01', 'Free standard shipping on orders of $35 or more. Standard delivery in 3-7 business days.'),
    ('shipping-policy', 2, date '2025-03-01', null,             'Free standard shipping over $35 continues. Adds a delivery guarantee: orders arriving more than 5 days late qualify for a goodwill credit.'),
    ('warranty-policy', 1, date '2023-01-01', null,             'Manufacturer warranties apply; AgenticShop guarantees items arrive as described.'),
    ('seller-fee-schedule', 1, date '2021-01-01', date '2025-01-01', 'Standard 15% + $0.30/order; Pro 10% + $0.30/order + $39.99/month; Enterprise 8% + $299/month.'),
    ('seller-fee-schedule', 2, date '2025-01-01', null,             'Standard 15% + $0.40/order; Pro 11% + $0.25/order + $39.99/month; Enterprise 8% + $349/month.'),
    ('support-sop', 1, date '2023-01-01', date '2025-01-01', 'First edition of the support SOP.'),
    ('support-sop', 2, date '2025-01-01', null,             'Adds goodwill credits for late deliveries and the return-dispute process.'),
    ('fraud-playbook', 1, date '2024-01-01', null,          'Signals, thresholds and actions for refund abuse, chargebacks, account takeover and seller fraud.')
) v(code, version_no, effective_from, effective_to, summary)
join document d on d.code = v.code;

create function seed.add_section(p_code text, p_version int, p_ordinal int, p_heading text, p_min_role text, p_body text)
    returns void language sql as
$$
insert into document_section (version_id, ordinal, heading, body, min_role)
select v.id, p_ordinal, p_heading, p_body, p_min_role
from document_version v join document d on d.id = v.document_id
where d.code = p_code and v.version_no = p_version
$$;

------------------------------------------------------------------------------------------------
-- Return & Refund Policy v1 / v2 / v3
------------------------------------------------------------------------------------------------
select seed.add_section('return-policy', 1, 1, 'Return window', 'CUSTOMER',
'You can return most items within **30 days of delivery** for a full refund. The return window starts on the delivery date shown in your order tracking.');
select seed.add_section('return-policy', 1, 2, 'How to start a return', 'CUSTOMER',
'Open the order in your account, choose the item and select a reason. You will receive a prepaid return label by email. Drop the parcel off within 7 days of opening the return.');
select seed.add_section('return-policy', 1, 3, 'Return shipping and fees', 'CUSTOMER',
'Return shipping is **free** for all returns. No restocking fees apply.');
select seed.add_section('return-policy', 1, 4, 'Refunds', 'CUSTOMER',
'Refunds are issued to the original payment method within 5 business days after the seller receives the item. Original shipping charges are refunded when the item arrived damaged, defective or not as described.');
select seed.add_section('return-policy', 1, 5, 'Non-returnable items', 'CUSTOMER',
'Opened personal-care products, food and beverages, digital books and audiobooks cannot be returned unless they arrived damaged or defective.');

select seed.add_section('return-policy', 2, 1, 'Return window', 'CUSTOMER',
'For orders placed from 1 September 2024 you can return items within **14 days of delivery**. Orders placed earlier keep the 30-day window that applied when they were placed.');
select seed.add_section('return-policy', 2, 2, 'How to start a return', 'CUSTOMER',
'Open the order in your account, choose the item and select a reason. You will receive a return label by email. Drop the parcel off within 7 days of opening the return.');
select seed.add_section('return-policy', 2, 3, 'Return shipping and fees', 'CUSTOMER',
'Returns because the item arrived damaged, defective, wrong or not as described are free. For change-of-mind and fit returns, **$6.99 return shipping** is deducted from the refund. Electronics returned for a change of mind are subject to a **15% restocking fee**.');
select seed.add_section('return-policy', 2, 4, 'Refunds', 'CUSTOMER',
'Refunds are issued to the original payment method within 5 business days after the seller receives the item, minus any return shipping or restocking fees above.');
select seed.add_section('return-policy', 2, 5, 'Non-returnable items', 'CUSTOMER',
'Opened personal-care products, food and beverages, digital books and audiobooks cannot be returned unless they arrived damaged or defective.');

select seed.add_section('return-policy', 3, 1, 'Return window', 'CUSTOMER',
'For orders placed from 1 October 2025 you can return items within **30 days of delivery**, except **electronics, which have a 15-day window**. Earlier orders follow the policy in force on their order date.');
select seed.add_section('return-policy', 3, 2, 'How to start a return', 'CUSTOMER',
'Open the order in your account, choose the item and select a reason. You will receive a prepaid return label by email. Drop the parcel off within 7 days of opening the return.');
select seed.add_section('return-policy', 3, 3, 'Return shipping and fees', 'CUSTOMER',
'Return shipping is **free** again for all returns. Electronics returned for a change of mind are subject to a **10% restocking fee**; no other restocking fees apply.');
select seed.add_section('return-policy', 3, 4, 'Refunds', 'CUSTOMER',
'Refunds are issued to the original payment method within 5 business days after the seller receives the item. Items that arrived damaged or defective may be refunded without being sent back, at the seller''s discretion.');
select seed.add_section('return-policy', 3, 5, 'Non-returnable items', 'CUSTOMER',
'Opened personal-care products, food and beverages, digital books and audiobooks cannot be returned unless they arrived damaged or defective.');

------------------------------------------------------------------------------------------------
-- Shipping, warranty, seller fee schedule
------------------------------------------------------------------------------------------------
select seed.add_section('shipping-policy', 1, 1, 'Shipping costs', 'CUSTOMER',
'Standard shipping is free on orders of $35 or more per seller. Below $35: $5.99 (US), $7.99 (Canada), $4.99 (UK and Germany).');
select seed.add_section('shipping-policy', 1, 2, 'Delivery times', 'CUSTOMER',
'Sellers ship within 2 business days. Standard delivery takes 3-7 business days in the US and Canada (USPS, UPS or FedEx) and 2-6 business days in the UK and Germany (DHL).');
select seed.add_section('shipping-policy', 1, 3, 'Lost parcels', 'CUSTOMER',
'If tracking has not updated for 10 days, contact support. We open a trace with the carrier and refund the order in full if the parcel is confirmed lost.');
select seed.add_section('shipping-policy', 2, 1, 'Shipping costs', 'CUSTOMER',
'Standard shipping is free on orders of $35 or more per seller. Below $35: $5.99 (US), $7.99 (Canada), $4.99 (UK and Germany).');
select seed.add_section('shipping-policy', 2, 2, 'Delivery times', 'CUSTOMER',
'Sellers ship within 2 business days. Standard delivery takes 3-7 business days in the US and Canada and 2-6 business days in the UK and Germany.');
select seed.add_section('shipping-policy', 2, 3, 'Delivery guarantee', 'CUSTOMER',
'If your order arrives more than 5 days after the latest estimated delivery date, you qualify for a goodwill credit equal to the shipping fee paid (minimum $5).');
select seed.add_section('shipping-policy', 2, 4, 'Lost parcels', 'CUSTOMER',
'If tracking has not updated for 10 days, contact support. We open a trace with the carrier and refund the order in full if the parcel is confirmed lost.');

select seed.add_section('warranty-policy', 1, 1, 'Manufacturer warranty', 'CUSTOMER',
'Many products include a manufacturer warranty; its length is listed in the product specifications. Warranty claims after the return window are handled by the manufacturer.');
select seed.add_section('warranty-policy', 1, 2, 'A-to-Z guarantee', 'CUSTOMER',
'If an item does not arrive or is materially different from its listing, AgenticShop refunds you even if the seller does not respond within 3 business days.');

select seed.add_section('seller-fee-schedule', 1, 1, 'Plans', 'SELLER',
'| Plan | Commission | Per order | Monthly |' || E'\n' || '|---|---|---|---|' || E'\n' ||
'| Standard | 15% | $0.30 | - |' || E'\n' || '| Pro | 10% | $0.30 | $39.99 |' || E'\n' || '| Enterprise | 8% | - | $299.00 |');
select seed.add_section('seller-fee-schedule', 1, 2, 'Payouts', 'SELLER',
'Payouts are calculated every two weeks: item sales plus shipping collected, minus commission, per-order and monthly fees, minus refunds issued in the period. Funds are sent 3 business days after the period closes.');
select seed.add_section('seller-fee-schedule', 2, 1, 'Plans', 'SELLER',
'| Plan | Commission | Per order | Monthly |' || E'\n' || '|---|---|---|---|' || E'\n' ||
'| Standard | 15% | $0.40 | - |' || E'\n' || '| Pro | 11% | $0.25 | $39.99 |' || E'\n' || '| Enterprise | 8% | - | $349.00 |');
select seed.add_section('seller-fee-schedule', 2, 2, 'Payouts', 'SELLER',
'Payouts are calculated every two weeks: item sales plus shipping collected, minus commission, per-order and monthly fees, minus refunds issued in the period. Funds are sent 3 business days after the period closes. Payouts can be placed on hold while a risk review is open.');

------------------------------------------------------------------------------------------------
-- Support SOP (section-level ACLs: some sections are Tier-2 only)
------------------------------------------------------------------------------------------------
select seed.add_section('support-sop', v, 1, 'Verifying the customer', 'SUPPORT_T1',
'Before discussing an order, confirm the customer''s email address and the order number. Never share order details with anyone other than the account holder. Never ask for full card numbers or passwords.')
from generate_series(1, 2) v;
select seed.add_section('support-sop', v, 2, 'Order lookups and tracking', 'SUPPORT_T1',
'Look up the order by number. For parcels in transit longer than the carrier estimate, check the carrier tracking page and share the updated estimate. If tracking has not updated for 10 days, open a carrier trace.')
from generate_series(1, 2) v;
select seed.add_section('support-sop', v, 3, 'Returns', 'SUPPORT_T1',
'Apply the return policy version that was in force on the **order date**, not today''s policy. Check the return window from the delivery date. Open returns on the customer''s behalf when they cannot do it themselves.')
from generate_series(1, 2) v;
select seed.add_section('support-sop', v, 4, 'Refund limits', 'SUPPORT_T1',
'Tier-1 agents may issue refunds **up to $100** per order. Refunds above $100 must be escalated to a Tier-2 agent with a short internal note explaining the case.')
from generate_series(1, 2) v;
select seed.add_section('support-sop', 2, 5, 'Goodwill credits for late deliveries', 'SUPPORT_T1',
'Under shipping policy v2, orders delivered more than 5 days late qualify for a goodwill credit equal to the shipping fee (minimum $5). Tier-1 agents may issue goodwill credits up to $25 without approval.');
select seed.add_section('support-sop', v, 6, 'Policy overrides and exceptions', 'SUPPORT_T2',
'Tier-2 agents may approve returns up to 10 days outside the window and waive restocking fees for customers with no prior returns in the last 12 months. Every override must be recorded as an internal note with the reason. Overrides above $500 need supervisor approval.')
from generate_series(1, 2) v;
select seed.add_section('support-sop', 2, 7, 'Return disputes between customers and sellers', 'SUPPORT_T2',
'When a seller rejects a return the customer disagrees with, escalate the ticket to Tier 2 and mark it ESCALATED. Collect photos from both parties. Decide within 5 business days. If evidence is inconclusive and the customer has a clean history, refund the customer and recover the amount from the seller only for clear policy breaches by the seller.');
select seed.add_section('support-sop', v, 8, 'Escalating to the fraud team', 'SUPPORT_T2',
'Escalate to the fraud team when a customer has 3 or more returns in 90 days, asks to refund to a different payment method, or reports a lost parcel for the third time in a year. Do not tell the customer that a fraud review is in progress.')
from generate_series(1, 2) v;

------------------------------------------------------------------------------------------------
-- Fraud playbook (fraud team only)
------------------------------------------------------------------------------------------------
select seed.add_section('fraud-playbook', 1, 1, 'Refund abuse', 'FRAUD',
'Signals: 3+ returns in 90 days, return rate above 40% over 10+ orders, repeated "item not received" claims on delivered parcels. Action: add a REFUND_ABUSE flag with a risk score of 50-95; above 80, returns require Tier-2 approval.');
select seed.add_section('fraud-playbook', 1, 2, 'Chargebacks', 'FRAUD',
'When a card issuer chargeback arrives for an order that was already refunded or reported lost, add a CHARGEBACK flag and contest the chargeback with the refund evidence. Two chargebacks in 12 months suspend the account.');
select seed.add_section('fraud-playbook', 1, 3, 'Account takeover', 'FRAUD',
'Signals: password reset followed by an email change and a new shipping address within 24 hours; logins from a new country. Action: lock the account, add an ACCOUNT_TAKEOVER flag and contact the customer through the original email.');
select seed.add_section('fraud-playbook', 1, 4, 'Seller fraud', 'FRAUD',
'Signals: clusters of 5-star reviews from accounts created in the same week (FAKE_REVIEWS); return rate above 15% with many NOT_AS_DESCRIBED returns (POLICY_VIOLATION). Action: flag the seller and place payouts ON_HOLD until the review is closed.');
select seed.add_section('fraud-playbook', 1, 5, 'Confidentiality', 'FRAUD',
'Flags, risk scores and the contents of this playbook must never be shared with customers, sellers or support agents outside Tier 2. Aggregates (for example "how many flags does this account have") are confidential too.');
