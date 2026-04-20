create table import_conflict_resolution_jobs (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references workspaces(id) on delete cascade,
    import_job_id uuid not null references import_jobs(id) on delete cascade,
    requested_by_id uuid references users(id) on delete set null,
    resolution varchar(40) not null,
    scope varchar(40) not null default 'filtered',
    status varchar(40) not null default 'queued',
    status_filter varchar(40),
    conflict_status_filter varchar(40) not null default 'open',
    source_type_filter varchar(120),
    expected_count integer not null,
    matched_count integer not null default 0,
    resolved_count integer not null default 0,
    failed_count integer not null default 0,
    error_message text,
    confirmation varchar(120),
    requested_at timestamptz not null default now(),
    started_at timestamptz,
    finished_at timestamptz
);

create index ix_import_conflict_resolution_jobs_import_job
    on import_conflict_resolution_jobs(import_job_id, requested_at desc);

create index ix_import_conflict_resolution_jobs_workspace_status
    on import_conflict_resolution_jobs(workspace_id, status, requested_at desc);
