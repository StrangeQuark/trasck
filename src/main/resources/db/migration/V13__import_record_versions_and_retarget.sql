create table import_job_record_versions (
    id uuid primary key default gen_random_uuid(),
    import_job_record_id uuid not null references import_job_records(id) on delete cascade,
    import_job_id uuid not null references import_jobs(id) on delete cascade,
    version integer not null,
    change_type varchar(80) not null,
    changed_by_id uuid references users(id) on delete set null,
    snapshot jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    unique(import_job_record_id, version)
);

create index ix_import_job_record_versions_record_version
    on import_job_record_versions(import_job_record_id, version desc);

create index ix_import_job_record_versions_job_created
    on import_job_record_versions(import_job_id, created_at desc);
