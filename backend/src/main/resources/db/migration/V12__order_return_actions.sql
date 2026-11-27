-- Write actions: order cancellation, staff decisions on returns

alter table customer_order
    add column cancelled_at        timestamptz,
    add column cancellation_reason varchar(500);

alter table return_request
    add column decided_by_id   bigint references app_user (id),
    add column resolution_note text;
