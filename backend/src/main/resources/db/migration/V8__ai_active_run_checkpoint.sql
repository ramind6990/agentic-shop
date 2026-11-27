-- ai agent: a conversation's active run and the graph checkpoints of that run.

-- The one run a conversation may have at a time; the row is the conversation's lock.
-- RUNNING: the graph is executing, any input is rejected. PAUSED: the graph waits for the answer to
-- pending_interaction (the JSON the UI got); pending_interaction_id is the id the answer must carry.
-- The row is deleted when the run completes, fails or is abandoned.
create table ai_active_run (
    conversation_id         bigint       primary key,
    run_id                  uuid         not null unique,
    user_id                 bigint       not null references app_user (id),
    status                  varchar(16)  not null check (status in ('RUNNING', 'PAUSED')),
    pending_interaction_id  uuid,
    pending_interaction     jsonb,
    started_at              timestamptz  not null,
    updated_at              timestamptz  not null,
    check ((status = 'PAUSED') = (pending_interaction_id is not null and pending_interaction is not null))
);

-- Graph state after each node of a run (langgraph4j thread = run id). A run's checkpoints live exactly as long as
-- its active run row (cascade), so a paused run can be resumed after a restart and nothing is left behind.
create table ai_checkpoint (
    id            varchar(64)   primary key,
    run_id        uuid          not null references ai_active_run (run_id) on delete cascade,
    seq           bigint generated always as identity,
    node_id       varchar(60),
    next_node_id  varchar(60),
    content_type  varchar(100)  not null,
    state_data    text          not null,
    created_at    timestamptz   not null
);

create index ix_ai_checkpoint_run_seq on ai_checkpoint (run_id, seq);
