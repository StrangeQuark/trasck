alter table automation_worker_settings
    add column worker_run_pruning_automatic_enabled boolean not null default false;

create table import_transform_presets (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references workspaces(id) on delete cascade,
    name varchar(255) not null,
    description text,
    transformation_config jsonb not null default '{}'::jsonb,
    enabled boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint import_transform_presets_workspace_name_key unique (workspace_id, name)
);

create trigger trg_import_transform_presets_updated_at
    before update on import_transform_presets
    for each row execute function set_updated_at();

create index ix_import_transform_presets_workspace_enabled
    on import_transform_presets(workspace_id, enabled, name);

alter table import_mapping_templates
    add column transform_preset_id uuid references import_transform_presets(id) on delete set null;

create index ix_import_mapping_templates_transform_preset
    on import_mapping_templates(transform_preset_id);
