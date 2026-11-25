-- Conversation memory: a rolling summary per ticket and audience, and LangGraph4j checkpoints per run.
-- (V10 is reserved for the RAG chunks.)

-- The older part of a ticket's timeline, folded into a bounded summary. One row per audience: CUSTOMER summarizes
-- only customer-visible messages (used for customer turns), STAFF all messages incl. internal notes (staff turns),
-- so an internal note can never reach a customer prompt through a summary.
create table ai_conversation_summary (
    ticket_id                bigint       not null references ticket (id) on delete cascade,
    audience                 varchar(20)  not null,
    summary                  text         not null,
    -- every visible message with id <= this is covered by the summary; the prompt carries only later ones raw
    covers_up_to_message_id  bigint       not null,
    message_count            int          not null,
    model                    varchar(80),
    prompt_version           varchar(40),
    updated_at               timestamptz  not null,
    version                  int          not null default 0,
    constraint pk_ai_conversation_summary primary key (ticket_id, audience)
);

-- Graph state after each node of a run (thread = run id). Deleted when the run ends; kept only for runs a restart
-- interrupted, so they can be resumed.
create table ai_checkpoint (
    id            varchar(64)  primary key,
    run_id        uuid         not null references ai_run (id) on delete cascade,
    seq           bigint generated always as identity,
    node_id       varchar(60),
    next_node_id  varchar(60),
    content_type  varchar(100) not null,
    state_data    text         not null,
    created_at    timestamptz  not null
);

create index ix_ai_checkpoint_run_seq on ai_checkpoint (run_id, seq);
