alter table import_mapping_templates
    add column transformation_config jsonb not null default '{}'::jsonb;

create table import_mapping_value_lookups (
    id uuid primary key default gen_random_uuid(),
    mapping_template_id uuid not null references import_mapping_templates(id) on delete cascade,
    source_field varchar(160) not null,
    source_value varchar(500) not null,
    target_field varchar(160) not null,
    target_value jsonb not null,
    sort_order integer not null default 0,
    enabled boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (mapping_template_id, source_field, source_value, target_field)
);

create trigger trg_import_mapping_value_lookups_updated_at
    before update on import_mapping_value_lookups
    for each row execute function set_updated_at();

create index ix_import_mapping_value_lookups_template_target
    on import_mapping_value_lookups(mapping_template_id, target_field, sort_order);

create table import_mapping_type_translations (
    id uuid primary key default gen_random_uuid(),
    mapping_template_id uuid not null references import_mapping_templates(id) on delete cascade,
    source_type_key varchar(160) not null,
    target_type_key varchar(160) not null,
    enabled boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (mapping_template_id, source_type_key)
);

create trigger trg_import_mapping_type_translations_updated_at
    before update on import_mapping_type_translations
    for each row execute function set_updated_at();

create table import_mapping_status_translations (
    id uuid primary key default gen_random_uuid(),
    mapping_template_id uuid not null references import_mapping_templates(id) on delete cascade,
    source_status_key varchar(160) not null,
    target_status_key varchar(160) not null,
    enabled boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (mapping_template_id, source_status_key)
);

create trigger trg_import_mapping_status_translations_updated_at
    before update on import_mapping_status_translations
    for each row execute function set_updated_at();

create table automation_worker_runs (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references workspaces(id) on delete cascade,
    worker_type varchar(40) not null,
    trigger_type varchar(40) not null,
    status varchar(40) not null default 'running',
    dry_run boolean,
    requested_limit integer,
    max_attempts integer,
    processed_count integer not null default 0,
    success_count integer not null default 0,
    failure_count integer not null default 0,
    dead_letter_count integer not null default 0,
    error_message text,
    metadata jsonb not null default '{}'::jsonb,
    started_at timestamptz not null default now(),
    finished_at timestamptz,
    constraint ck_automation_worker_runs_worker_type check (worker_type in ('automation', 'webhook', 'email')),
    constraint ck_automation_worker_runs_trigger_type check (trigger_type in ('manual', 'scheduled')),
    constraint ck_automation_worker_runs_status check (status in ('running', 'succeeded', 'failed'))
);

create index ix_automation_worker_runs_workspace_started
    on automation_worker_runs(workspace_id, started_at desc);

create index ix_automation_worker_runs_workspace_type
    on automation_worker_runs(workspace_id, worker_type, started_at desc);

create table automation_worker_health (
    workspace_id uuid not null references workspaces(id) on delete cascade,
    worker_type varchar(40) not null,
    last_run_id uuid references automation_worker_runs(id) on delete set null,
    last_status varchar(40),
    last_started_at timestamptz,
    last_finished_at timestamptz,
    consecutive_failures integer not null default 0,
    last_error text,
    updated_at timestamptz not null default now(),
    primary key (workspace_id, worker_type),
    constraint ck_automation_worker_health_worker_type check (worker_type in ('automation', 'webhook', 'email')),
    constraint ck_automation_worker_health_status check (last_status is null or last_status in ('running', 'succeeded', 'failed'))
);

create trigger trg_automation_worker_health_updated_at
    before update on automation_worker_health
    for each row execute function set_updated_at();
