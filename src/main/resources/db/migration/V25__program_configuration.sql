alter table programs
    add column roadmap_config jsonb not null default '{}'::jsonb,
    add column report_config jsonb not null default '{}'::jsonb;

create index ix_programs_workspace_status_name on programs(workspace_id, status, name);
