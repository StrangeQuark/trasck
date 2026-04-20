create table import_mapping_templates (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references workspaces(id) on delete cascade,
    project_id uuid references projects(id) on delete cascade,
    name varchar(160) not null,
    provider varchar(80) not null,
    source_type varchar(80),
    target_type varchar(80) not null default 'work_item',
    work_item_type_key varchar(80),
    status_key varchar(80),
    field_mapping jsonb not null default '{}'::jsonb,
    defaults jsonb not null default '{}'::jsonb,
    enabled boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (workspace_id, name)
);

create trigger trg_import_mapping_templates_updated_at
    before update on import_mapping_templates
    for each row execute function set_updated_at();

create index ix_import_mapping_templates_workspace_provider
    on import_mapping_templates(workspace_id, provider, source_type);

create table automation_worker_settings (
    workspace_id uuid primary key references workspaces(id) on delete cascade,
    automation_jobs_enabled boolean not null default false,
    webhook_deliveries_enabled boolean not null default false,
    email_deliveries_enabled boolean not null default false,
    automation_limit integer not null default 25,
    webhook_limit integer not null default 25,
    email_limit integer not null default 25,
    webhook_max_attempts integer not null default 3,
    email_max_attempts integer not null default 3,
    webhook_dry_run boolean not null default true,
    email_dry_run boolean not null default true,
    updated_at timestamptz not null default now(),
    constraint ck_automation_worker_settings_limits check (
        automation_limit between 1 and 100
        and webhook_limit between 1 and 100
        and email_limit between 1 and 100
        and webhook_max_attempts between 1 and 20
        and email_max_attempts between 1 and 20
    )
);

create trigger trg_automation_worker_settings_updated_at
    before update on automation_worker_settings
    for each row execute function set_updated_at();

create table email_deliveries (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references workspaces(id) on delete cascade,
    automation_job_id uuid references automation_execution_jobs(id) on delete set null,
    action_id uuid references automation_actions(id) on delete set null,
    provider varchar(80) not null default 'maildev',
    from_email varchar(320),
    recipient_email varchar(320) not null,
    subject varchar(300) not null,
    body text,
    status varchar(40) not null default 'queued',
    attempt_count integer not null default 0,
    response_body text,
    next_retry_at timestamptz,
    created_at timestamptz not null default now(),
    sent_at timestamptz,
    constraint ck_email_deliveries_status check (status in ('queued', 'sent', 'failed', 'cancelled', 'dead_letter'))
);

create index ix_email_deliveries_workspace_status_next
    on email_deliveries(workspace_id, status, next_retry_at);

create index ix_email_deliveries_job
    on email_deliveries(automation_job_id);
