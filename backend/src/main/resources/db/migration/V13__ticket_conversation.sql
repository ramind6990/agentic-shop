-- Hand-off: a ticket opened from an agent conversation keeps a link to it

alter table ticket add column conversation_id bigint references ai_conversation (id);

create index idx_ticket_customer_status on ticket (customer_id, status);
create index idx_ticket_queue on ticket (status, tier, assignee_id);
