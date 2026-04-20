alter table automation_worker_settings
    add column worker_run_pruning_interval_minutes integer not null default 1440,
    add column worker_run_pruning_window_start time,
    add column worker_run_pruning_window_end time,
    add column worker_run_pruning_last_started_at timestamptz,
    add column worker_run_pruning_last_finished_at timestamptz;

alter table automation_worker_settings
    add constraint ck_automation_worker_settings_pruning_schedule check (
        worker_run_pruning_interval_minutes between 5 and 10080
    );

alter table import_transform_presets
    add column version integer not null default 1;

alter table board_swimlanes
    add column saved_filter_id uuid references saved_filters(id) on delete set null;

create index ix_board_swimlanes_saved_filter
    on board_swimlanes(saved_filter_id);

create table import_materialization_runs (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references workspaces(id) on delete cascade,
    import_job_id uuid not null references import_jobs(id) on delete cascade,
    mapping_template_id uuid references import_mapping_templates(id) on delete set null,
    transform_preset_id uuid references import_transform_presets(id) on delete set null,
    transform_preset_version integer,
    project_id uuid references projects(id) on delete set null,
    requested_by_id uuid references users(id) on delete set null,
    update_existing boolean not null default false,
    mapping_template_snapshot jsonb not null default '{}'::jsonb,
    transform_preset_snapshot jsonb,
    transformation_config_snapshot jsonb not null default '{}'::jsonb,
    status varchar(40) not null default 'running',
    records_processed integer not null default 0,
    records_created integer not null default 0,
    records_updated integer not null default 0,
    records_failed integer not null default 0,
    created_at timestamptz not null default now(),
    finished_at timestamptz,
    constraint ck_import_materialization_runs_status check (status in ('running', 'completed', 'completed_with_failures', 'failed'))
);

create index ix_import_materialization_runs_job
    on import_materialization_runs(import_job_id, created_at desc);

create index ix_import_materialization_runs_workspace
    on import_materialization_runs(workspace_id, created_at desc);
