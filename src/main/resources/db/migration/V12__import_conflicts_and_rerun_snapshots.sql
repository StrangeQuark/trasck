alter table import_job_records
    add column conflict_status varchar(40),
    add column conflict_reason text,
    add column conflict_detected_at timestamptz,
    add column conflict_resolved_at timestamptz,
    add column conflict_resolution varchar(40),
    add column conflict_resolved_by_id uuid references users(id) on delete set null,
    add column conflict_materialization_run_id uuid references import_materialization_runs(id) on delete set null,
    add constraint ck_import_job_records_conflict_status
        check (conflict_status is null or conflict_status in ('open', 'resolved')),
    add constraint ck_import_job_records_conflict_resolution
        check (conflict_resolution is null or conflict_resolution in ('skip', 'update_existing', 'create_new'));

create index ix_import_job_records_conflict_status
    on import_job_records(import_job_id, conflict_status, source_type, source_id);

alter table import_materialization_runs
    add column mapping_rules_snapshot jsonb not null default '{}'::jsonb;
