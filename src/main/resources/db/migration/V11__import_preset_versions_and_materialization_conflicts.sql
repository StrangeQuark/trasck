create table import_transform_preset_versions (
    id uuid primary key default gen_random_uuid(),
    preset_id uuid not null references import_transform_presets(id) on delete cascade,
    workspace_id uuid not null references workspaces(id) on delete cascade,
    version integer not null,
    name varchar(255) not null,
    description text,
    transformation_config jsonb not null default '{}'::jsonb,
    enabled boolean not null default true,
    change_type varchar(40) not null default 'updated',
    created_by_id uuid references users(id) on delete set null,
    created_at timestamptz not null default now(),
    unique (preset_id, version),
    constraint ck_import_transform_preset_versions_change_type check (change_type in ('created', 'updated', 'disabled'))
);

insert into import_transform_preset_versions (
    preset_id,
    workspace_id,
    version,
    name,
    description,
    transformation_config,
    enabled,
    change_type,
    created_at
)
select
    id,
    workspace_id,
    version,
    name,
    description,
    transformation_config,
    enabled,
    'created',
    created_at
from import_transform_presets;

create index ix_import_transform_preset_versions_preset
    on import_transform_preset_versions(preset_id, version desc);

create index ix_import_transform_preset_versions_workspace
    on import_transform_preset_versions(workspace_id, created_at desc);

alter table import_materialization_runs
    add column records_skipped integer not null default 0,
    add column records_conflicted integer not null default 0;
