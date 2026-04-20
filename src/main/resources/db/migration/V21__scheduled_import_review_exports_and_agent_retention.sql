alter table automation_worker_settings
    add column import_review_exports_enabled boolean not null default false,
    add column import_review_export_limit integer not null default 10,
    add column agent_dispatch_attempt_retention_enabled boolean not null default false,
    add column agent_dispatch_attempt_retention_days integer not null default 30,
    add column agent_dispatch_attempt_export_before_prune boolean not null default true,
    add column agent_dispatch_attempt_pruning_automatic_enabled boolean not null default false,
    add column agent_dispatch_attempt_pruning_interval_minutes integer not null default 1440,
    add column agent_dispatch_attempt_pruning_window_start time,
    add column agent_dispatch_attempt_pruning_window_end time,
    add column agent_dispatch_attempt_pruning_last_started_at timestamptz,
    add column agent_dispatch_attempt_pruning_last_finished_at timestamptz;

alter table automation_worker_settings
    add constraint ck_automation_worker_settings_import_review_export_limit check (
        import_review_export_limit between 1 and 50
    ),
    add constraint ck_automation_worker_settings_agent_dispatch_retention_days check (
        agent_dispatch_attempt_retention_days between 1 and 3650
    ),
    add constraint ck_automation_worker_settings_agent_dispatch_pruning_interval check (
        agent_dispatch_attempt_pruning_interval_minutes between 5 and 10080
    );

alter table automation_worker_runs
    drop constraint ck_automation_worker_runs_worker_type;

alter table automation_worker_runs
    add constraint ck_automation_worker_runs_worker_type check (
        worker_type in ('automation', 'webhook', 'email', 'import_conflict_resolution', 'import_review_export')
    );

alter table automation_worker_health
    drop constraint ck_automation_worker_health_worker_type;

alter table automation_worker_health
    add constraint ck_automation_worker_health_worker_type check (
        worker_type in ('automation', 'webhook', 'email', 'import_conflict_resolution', 'import_review_export')
    );
