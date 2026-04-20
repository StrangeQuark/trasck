create table agent_dispatch_attempts (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references workspaces(id) on delete cascade,
    agent_task_id uuid not null references agent_tasks(id) on delete cascade,
    provider_id uuid not null references agent_providers(id) on delete restrict,
    agent_profile_id uuid not null references agent_profiles(id) on delete restrict,
    work_item_id uuid not null references work_items(id) on delete cascade,
    requested_by_id uuid references users(id) on delete set null,
    attempt_type varchar(40) not null,
    dispatch_mode varchar(40) not null,
    provider_type varchar(80) not null,
    transport varchar(80),
    status varchar(40) not null,
    external_task_id varchar(255),
    idempotency_key varchar(255),
    external_dispatch boolean not null default false,
    request_payload jsonb not null default '{}'::jsonb,
    response_payload jsonb not null default '{}'::jsonb,
    error_message text,
    started_at timestamptz not null default now(),
    finished_at timestamptz,
    constraint ck_agent_dispatch_attempts_type check (attempt_type in ('dispatch', 'retry', 'cancel')),
    constraint ck_agent_dispatch_attempts_status check (status in ('succeeded', 'failed'))
);

create index ix_agent_dispatch_attempts_workspace_started
    on agent_dispatch_attempts(workspace_id, started_at desc);

create index ix_agent_dispatch_attempts_task_started
    on agent_dispatch_attempts(agent_task_id, started_at asc, id asc);

create index ix_agent_dispatch_attempts_provider_status
    on agent_dispatch_attempts(provider_id, status, started_at desc);
