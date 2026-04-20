alter table export_jobs
    add column request_payload jsonb not null default '{}'::jsonb,
    add column error_message text,
    add column created_at timestamptz not null default now();

create index ix_export_jobs_workspace_type_status_created
    on export_jobs(workspace_id, export_type, status, created_at desc);

create index ix_agent_dispatch_attempts_workspace_filters
    on agent_dispatch_attempts(workspace_id, attempt_type, status, started_at desc);
