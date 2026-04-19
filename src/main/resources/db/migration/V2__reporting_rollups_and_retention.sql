create table reporting_retention_policies (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references workspaces(id) on delete cascade,
    raw_retention_days integer not null default 730,
    weekly_rollup_after_days integer not null default 90,
    monthly_rollup_after_days integer not null default 365,
    archive_after_days integer not null default 1825,
    destructive_pruning_enabled boolean not null default false,
    created_by_id uuid references users(id) on delete set null,
    updated_by_id uuid references users(id) on delete set null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (workspace_id),
    constraint ck_reporting_retention_policy_windows check (
        raw_retention_days > 0
        and weekly_rollup_after_days > 0
        and monthly_rollup_after_days > 0
        and archive_after_days > 0
        and weekly_rollup_after_days <= monthly_rollup_after_days
        and monthly_rollup_after_days <= raw_retention_days
        and raw_retention_days <= archive_after_days
    )
);

create function reporting_bucket_end(bucket_start date, bucket_granularity varchar)
returns date
language sql
immutable
as $$
    select case bucket_granularity
        when 'daily' then bucket_start
        when 'weekly' then bucket_start + 6
        when 'monthly' then (bucket_start + interval '1 month - 1 day')::date
        else bucket_start
    end
$$;

create table reporting_snapshot_archive_runs (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references workspaces(id) on delete cascade,
    retention_policy_id uuid references reporting_retention_policies(id) on delete set null,
    requested_by_id uuid references users(id) on delete set null,
    action varchar(80) not null,
    granularity varchar(20) not null,
    from_date date not null,
    to_date date not null,
    policy_snapshot jsonb not null default '{}'::jsonb,
    cycle_time_rollups integer not null default 0,
    iteration_rollups integer not null default 0,
    velocity_rollups integer not null default 0,
    cumulative_flow_rollups integer not null default 0,
    generic_rollups integer not null default 0,
    raw_rows_pruned integer not null default 0,
    status varchar(40) not null default 'completed',
    error_message text,
    started_at timestamptz not null default now(),
    completed_at timestamptz,
    constraint ck_reporting_snapshot_archive_runs_action check (action in ('rollup_run', 'rollup_backfill', 'retention_prune')),
    constraint ck_reporting_snapshot_archive_runs_granularity check (granularity in ('daily', 'weekly', 'monthly')),
    constraint ck_reporting_snapshot_archive_runs_status check (status in ('running', 'completed', 'failed')),
    constraint ck_reporting_snapshot_archive_runs_window check (from_date <= to_date)
);

create table reporting_cycle_time_rollups (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references workspaces(id) on delete cascade,
    project_id uuid not null references projects(id) on delete cascade,
    granularity varchar(20) not null,
    bucket_start_date date not null,
    bucket_end_date date not null,
    work_item_count integer not null default 0,
    lead_time_minutes_sum bigint not null default 0,
    cycle_time_minutes_sum bigint not null default 0,
    lead_time_minutes_avg numeric(12, 2) not null default 0,
    cycle_time_minutes_avg numeric(12, 2) not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (project_id, granularity, bucket_start_date),
    constraint ck_reporting_cycle_time_rollups_granularity check (granularity in ('daily', 'weekly', 'monthly')),
    constraint ck_reporting_cycle_time_rollups_bucket check (bucket_start_date <= bucket_end_date)
);

create table reporting_iteration_rollups (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references workspaces(id) on delete cascade,
    project_id uuid not null references projects(id) on delete cascade,
    team_id uuid references teams(id) on delete set null,
    team_scope_key uuid not null,
    granularity varchar(20) not null,
    bucket_start_date date not null,
    bucket_end_date date not null,
    iteration_count integer not null default 0,
    committed_points numeric(12, 2) not null default 0,
    completed_points numeric(12, 2) not null default 0,
    remaining_points numeric(12, 2) not null default 0,
    scope_added_points numeric(12, 2) not null default 0,
    scope_removed_points numeric(12, 2) not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (project_id, team_scope_key, granularity, bucket_start_date),
    constraint ck_reporting_iteration_rollups_granularity check (granularity in ('daily', 'weekly', 'monthly')),
    constraint ck_reporting_iteration_rollups_bucket check (bucket_start_date <= bucket_end_date)
);

create table reporting_velocity_rollups (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references workspaces(id) on delete cascade,
    project_id uuid not null references projects(id) on delete cascade,
    team_id uuid not null references teams(id) on delete cascade,
    granularity varchar(20) not null,
    bucket_start_date date not null,
    bucket_end_date date not null,
    iteration_count integer not null default 0,
    committed_points numeric(12, 2) not null default 0,
    completed_points numeric(12, 2) not null default 0,
    carried_over_points numeric(12, 2) not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (project_id, team_id, granularity, bucket_start_date),
    constraint ck_reporting_velocity_rollups_granularity check (granularity in ('daily', 'weekly', 'monthly')),
    constraint ck_reporting_velocity_rollups_bucket check (bucket_start_date <= bucket_end_date)
);

create table reporting_cumulative_flow_rollups (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references workspaces(id) on delete cascade,
    project_id uuid not null references projects(id) on delete cascade,
    board_id uuid not null references boards(id) on delete cascade,
    status_id uuid not null references workflow_statuses(id) on delete cascade,
    granularity varchar(20) not null,
    bucket_start_date date not null,
    bucket_end_date date not null,
    snapshot_count integer not null default 0,
    work_item_count_avg numeric(12, 2) not null default 0,
    work_item_count_max integer not null default 0,
    total_points_avg numeric(12, 2) not null default 0,
    total_points_max numeric(12, 2) not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (board_id, status_id, granularity, bucket_start_date),
    constraint ck_reporting_cumulative_flow_rollups_granularity check (granularity in ('daily', 'weekly', 'monthly')),
    constraint ck_reporting_cumulative_flow_rollups_bucket check (bucket_start_date <= bucket_end_date)
);

create table reporting_rollups (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references workspaces(id) on delete cascade,
    rollup_family varchar(120) not null,
    source varchar(40) not null default 'custom',
    granularity varchar(20) not null,
    bucket_start_date date not null,
    bucket_end_date date not null,
    dimension_type varchar(80),
    dimension_id uuid,
    metrics jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_reporting_rollups_source check (source in ('custom', 'experimental')),
    constraint ck_reporting_rollups_granularity check (granularity in ('daily', 'weekly', 'monthly')),
    constraint ck_reporting_rollups_bucket check (bucket_start_date <= bucket_end_date)
);

create unique index ux_reporting_rollups_dimension
    on reporting_rollups (
        workspace_id,
        rollup_family,
        source,
        granularity,
        bucket_start_date,
        coalesce(dimension_type, ''),
        coalesce(dimension_id, '00000000-0000-0000-0000-000000000000'::uuid)
    );

create index idx_reporting_archive_runs_workspace_started on reporting_snapshot_archive_runs(workspace_id, started_at desc);
create index idx_reporting_cycle_time_rollups_workspace_bucket on reporting_cycle_time_rollups(workspace_id, granularity, bucket_start_date);
create index idx_reporting_iteration_rollups_workspace_bucket on reporting_iteration_rollups(workspace_id, granularity, bucket_start_date);
create index idx_reporting_velocity_rollups_workspace_bucket on reporting_velocity_rollups(workspace_id, granularity, bucket_start_date);
create index idx_reporting_cumulative_flow_rollups_workspace_bucket on reporting_cumulative_flow_rollups(workspace_id, granularity, bucket_start_date);
create index idx_reporting_rollups_workspace_bucket on reporting_rollups(workspace_id, rollup_family, granularity, bucket_start_date);
