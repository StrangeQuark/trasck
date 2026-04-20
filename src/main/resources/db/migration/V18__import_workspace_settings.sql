create table import_workspace_settings (
    workspace_id uuid primary key references workspaces(id) on delete cascade,
    sample_jobs_enabled boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create trigger trg_import_workspace_settings_updated_at
    before update on import_workspace_settings
    for each row execute function set_updated_at();
