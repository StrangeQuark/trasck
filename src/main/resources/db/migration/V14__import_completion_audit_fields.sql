alter table import_jobs
    add column open_conflict_completion_accepted boolean not null default false,
    add column open_conflict_completion_count integer not null default 0,
    add column open_conflict_completed_by_id uuid references users(id) on delete set null,
    add column open_conflict_completed_at timestamptz,
    add column open_conflict_completion_reason text;

create index ix_import_jobs_open_conflict_completion
    on import_jobs(workspace_id, open_conflict_completed_at desc)
    where open_conflict_completion_accepted = true;
