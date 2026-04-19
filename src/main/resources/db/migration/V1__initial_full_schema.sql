create extension if not exists pgcrypto;

create table users (
    id uuid primary key default gen_random_uuid(),
    email varchar(320) not null,
    username varchar(80) not null,
    display_name varchar(160) not null,
    avatar_url text,
    account_type varchar(40) not null default 'human',
    password_hash text,
    email_verified boolean not null default false,
    active boolean not null default true,
    last_login_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    constraint ck_users_account_type check (account_type in ('human', 'agent', 'service'))
);

create unique index ux_users_email_lower on users (lower(email));
create unique index ux_users_username_lower on users (lower(username));

create table user_auth_identities (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references users(id) on delete cascade,
    provider varchar(80) not null,
    provider_subject varchar(255) not null,
    provider_username varchar(160),
    provider_email varchar(320),
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (provider, provider_subject)
);

create table organizations (
    id uuid primary key default gen_random_uuid(),
    name varchar(160) not null,
    slug varchar(120) not null,
    status varchar(40) not null default 'active',
    created_by_id uuid references users(id) on delete set null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    version bigint not null default 0,
    unique (slug)
);

create table workspaces (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organizations(id) on delete cascade,
    name varchar(160) not null,
    key varchar(32) not null,
    timezone varchar(80) not null default 'UTC',
    locale varchar(40) not null default 'en-US',
    status varchar(40) not null default 'active',
    anonymous_read_enabled boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    version bigint not null default 0,
    unique (organization_id, key)
);

create table projects (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references workspaces(id) on delete cascade,
    parent_project_id uuid references projects(id) on delete restrict,
    name varchar(160) not null,
    key varchar(32) not null,
    description text,
    visibility varchar(40) not null default 'private',
    status varchar(40) not null default 'active',
    lead_user_id uuid references users(id) on delete set null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    version bigint not null default 0,
    constraint ck_projects_visibility check (visibility in ('private', 'workspace', 'public')),
    unique (workspace_id, key)
);

create table workspace_work_item_sequences (
    workspace_id uuid primary key references workspaces(id) on delete cascade,
    next_value bigint not null default 1,
    updated_at timestamptz not null default now()
);

create table project_work_item_sequences (
    project_id uuid primary key references projects(id) on delete cascade,
    workspace_id uuid not null references workspaces(id) on delete cascade,
    next_value bigint not null default 1,
    updated_at timestamptz not null default now()
);

create table programs (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references workspaces(id) on delete cascade,
    name varchar(160) not null,
    description text,
    status varchar(40) not null default 'active',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table program_projects (
    program_id uuid not null references programs(id) on delete cascade,
    project_id uuid not null references projects(id) on delete cascade,
    position integer not null default 0,
    created_at timestamptz not null default now(),
    primary key (program_id, project_id)
);

create table teams (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references workspaces(id) on delete cascade,
    name varchar(160) not null,
    description text,
    lead_user_id uuid references users(id) on delete set null,
    default_capacity integer,
    status varchar(40) not null default 'active',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (workspace_id, name)
);

create table team_memberships (
    id uuid primary key default gen_random_uuid(),
    team_id uuid not null references teams(id) on delete cascade,
    user_id uuid not null references users(id) on delete cascade,
    role varchar(80) not null default 'member',
    capacity_percent integer not null default 100,
    joined_at timestamptz not null default now(),
    left_at timestamptz,
    unique (team_id, user_id)
);

create table project_teams (
    project_id uuid not null references projects(id) on delete cascade,
    team_id uuid not null references teams(id) on delete cascade,
    role varchar(80) not null default 'delivery',
    created_at timestamptz not null default now(),
    primary key (project_id, team_id)
);

create table roles (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid references workspaces(id) on delete cascade,
    project_id uuid references projects(id) on delete cascade,
    name varchar(120) not null,
    key varchar(80) not null,
    scope varchar(40) not null,
    description text,
    system_role boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_roles_scope check (scope in ('workspace', 'project')),
    constraint ck_roles_scope_owner check (
        (scope = 'workspace' and workspace_id is not null and project_id is null)
        or (scope = 'project' and project_id is not null)
    )
);

create unique index ux_roles_workspace_key on roles(workspace_id, key) where workspace_id is not null and project_id is null;
create unique index ux_roles_project_key on roles(project_id, key) where project_id is not null;

create table permissions (
    id uuid primary key default gen_random_uuid(),
    key varchar(120) not null unique,
    name varchar(160) not null,
    description text,
    category varchar(80) not null
);

create table role_permissions (
    role_id uuid not null references roles(id) on delete cascade,
    permission_id uuid not null references permissions(id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (role_id, permission_id)
);

create table user_invitations (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references workspaces(id) on delete cascade,
    project_id uuid references projects(id) on delete cascade,
    email varchar(320) not null,
    role_id uuid references roles(id) on delete set null,
    project_role_id uuid references roles(id) on delete set null,
    token_hash text not null unique,
    status varchar(40) not null default 'pending',
    invited_by_id uuid references users(id) on delete set null,
    accepted_by_id uuid references users(id) on delete set null,
    expires_at timestamptz not null,
    accepted_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_user_invitations_status check (status in ('pending', 'accepted', 'revoked', 'expired'))
);

create table api_tokens (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid references workspaces(id) on delete cascade,
    user_id uuid not null references users(id) on delete cascade,
    token_type varchar(40) not null,
    name varchar(160) not null,
    token_prefix varchar(24) not null,
    token_hash text not null unique,
    role_id uuid references roles(id) on delete set null,
    scopes jsonb not null default '[]'::jsonb,
    created_by_id uuid references users(id) on delete set null,
    expires_at timestamptz,
    last_used_at timestamptz,
    revoked_at timestamptz,
    revoked_by_id uuid references users(id) on delete set null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_api_tokens_type check (token_type in ('personal', 'service'))
);

create table workspace_memberships (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references workspaces(id) on delete cascade,
    user_id uuid not null references users(id) on delete cascade,
    role_id uuid references roles(id) on delete set null,
    status varchar(40) not null default 'active',
    invited_at timestamptz,
    joined_at timestamptz,
    created_at timestamptz not null default now(),
    unique (workspace_id, user_id)
);

create table project_memberships (
    id uuid primary key default gen_random_uuid(),
    project_id uuid not null references projects(id) on delete cascade,
    user_id uuid not null references users(id) on delete cascade,
    role_id uuid references roles(id) on delete set null,
    status varchar(40) not null default 'active',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (project_id, user_id)
);

create table work_item_types (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references workspaces(id) on delete cascade,
    name varchar(120) not null,
    key varchar(80) not null,
    icon varchar(80),
    color varchar(32),
    hierarchy_level integer not null,
    is_default boolean not null default false,
    is_leaf boolean not null default false,
    enabled boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (workspace_id, key)
);

create table work_item_type_rules (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references workspaces(id) on delete cascade,
    parent_type_id uuid not null references work_item_types(id) on delete cascade,
    child_type_id uuid not null references work_item_types(id) on delete cascade,
    enabled boolean not null default true,
    created_at timestamptz not null default now(),
    unique (workspace_id, parent_type_id, child_type_id),
    constraint ck_work_item_type_rules_not_self check (parent_type_id <> child_type_id)
);

create table project_work_item_types (
    id uuid primary key default gen_random_uuid(),
    project_id uuid not null references projects(id) on delete cascade,
    work_item_type_id uuid not null references work_item_types(id) on delete cascade,
    enabled boolean not null default true,
    default_type boolean not null default false,
    unique (project_id, work_item_type_id)
);

create table priorities (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references workspaces(id) on delete cascade,
    name varchar(80) not null,
    key varchar(80) not null,
    color varchar(32),
    sort_order integer not null default 0,
    is_default boolean not null default false,
    unique (workspace_id, key)
);

create table resolutions (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references workspaces(id) on delete cascade,
    name varchar(80) not null,
    key varchar(80) not null,
    category varchar(80) not null default 'done',
    sort_order integer not null default 0,
    is_default boolean not null default false,
    unique (workspace_id, key)
);

create table workflows (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references workspaces(id) on delete cascade,
    name varchar(160) not null,
    description text,
    active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version bigint not null default 0,
    unique (workspace_id, name)
);

create table workflow_statuses (
    id uuid primary key default gen_random_uuid(),
    workflow_id uuid not null references workflows(id) on delete cascade,
    name varchar(120) not null,
    key varchar(80) not null,
    category varchar(40) not null,
    color varchar(32),
    sort_order integer not null default 0,
    terminal boolean not null default false,
    constraint ck_workflow_statuses_category check (category in ('todo', 'in_progress', 'done')),
    unique (workflow_id, key)
);

create table workflow_transitions (
    id uuid primary key default gen_random_uuid(),
    workflow_id uuid not null references workflows(id) on delete cascade,
    from_status_id uuid references workflow_statuses(id) on delete cascade,
    to_status_id uuid not null references workflow_statuses(id) on delete cascade,
    name varchar(120) not null,
    key varchar(80) not null,
    global_transition boolean not null default false,
    sort_order integer not null default 0,
    unique (workflow_id, key)
);

create table workflow_transition_rules (
    id uuid primary key default gen_random_uuid(),
    transition_id uuid not null references workflow_transitions(id) on delete cascade,
    rule_type varchar(80) not null,
    config jsonb not null default '{}'::jsonb,
    error_message text,
    position integer not null default 0,
    enabled boolean not null default true
);

create table workflow_transition_actions (
    id uuid primary key default gen_random_uuid(),
    transition_id uuid not null references workflow_transitions(id) on delete cascade,
    action_type varchar(80) not null,
    config jsonb not null default '{}'::jsonb,
    position integer not null default 0,
    enabled boolean not null default true
);

create table workflow_assignments (
    id uuid primary key default gen_random_uuid(),
    project_id uuid not null references projects(id) on delete cascade,
    work_item_type_id uuid not null references work_item_types(id) on delete cascade,
    workflow_id uuid not null references workflows(id) on delete restrict,
    default_for_project boolean not null default false,
    unique (project_id, work_item_type_id)
);

create table components (
    id uuid primary key default gen_random_uuid(),
    project_id uuid not null references projects(id) on delete cascade,
    owner_team_id uuid references teams(id) on delete set null,
    name varchar(120) not null,
    description text,
    archived boolean not null default false,
    created_at timestamptz not null default now(),
    unique (project_id, name)
);

create table work_items (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references workspaces(id) on delete cascade,
    project_id uuid not null references projects(id) on delete cascade,
    type_id uuid not null references work_item_types(id) on delete restrict,
    parent_id uuid references work_items(id) on delete restrict,
    status_id uuid not null references workflow_statuses(id) on delete restrict,
    priority_id uuid references priorities(id) on delete set null,
    resolution_id uuid references resolutions(id) on delete set null,
    team_id uuid references teams(id) on delete set null,
    assignee_id uuid references users(id) on delete set null,
    reporter_id uuid references users(id) on delete set null,
    key varchar(80) not null,
    sequence_number bigint not null,
    workspace_sequence_number bigint not null,
    title varchar(500) not null,
    description_markdown text,
    description_document jsonb,
    visibility varchar(40) not null default 'inherited',
    estimate_points numeric(12, 2),
    estimate_minutes integer,
    remaining_minutes integer,
    rank varchar(64) not null default '0000001000000000',
    start_date date,
    due_date date,
    resolved_at timestamptz,
    created_by_id uuid references users(id) on delete set null,
    updated_by_id uuid references users(id) on delete set null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    version bigint not null default 0,
    constraint ck_work_items_visibility check (visibility in ('inherited', 'private', 'public')),
    constraint ck_work_items_no_self_parent check (parent_id is null or parent_id <> id),
    constraint ck_work_items_estimate_minutes check (estimate_minutes is null or estimate_minutes >= 0),
    constraint ck_work_items_remaining_minutes check (remaining_minutes is null or remaining_minutes >= 0),
    unique (workspace_id, key),
    unique (project_id, sequence_number),
    unique (workspace_id, workspace_sequence_number)
);

create table work_item_closure (
    workspace_id uuid not null references workspaces(id) on delete cascade,
    ancestor_work_item_id uuid not null references work_items(id) on delete cascade,
    descendant_work_item_id uuid not null references work_items(id) on delete cascade,
    depth integer not null,
    created_at timestamptz not null default now(),
    primary key (ancestor_work_item_id, descendant_work_item_id),
    constraint ck_work_item_closure_depth check (depth >= 0)
);

create table work_item_links (
    id uuid primary key default gen_random_uuid(),
    source_work_item_id uuid not null references work_items(id) on delete cascade,
    target_work_item_id uuid not null references work_items(id) on delete cascade,
    link_type varchar(80) not null,
    created_by_id uuid references users(id) on delete set null,
    created_at timestamptz not null default now(),
    constraint ck_work_item_links_not_self check (source_work_item_id <> target_work_item_id),
    unique (source_work_item_id, target_work_item_id, link_type)
);

create table labels (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references workspaces(id) on delete cascade,
    name varchar(120) not null,
    color varchar(32),
    created_at timestamptz not null default now(),
    unique (workspace_id, name)
);

create table work_item_labels (
    work_item_id uuid not null references work_items(id) on delete cascade,
    label_id uuid not null references labels(id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (work_item_id, label_id)
);

create table work_item_components (
    work_item_id uuid not null references work_items(id) on delete cascade,
    component_id uuid not null references components(id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (work_item_id, component_id)
);

create table boards (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references workspaces(id) on delete cascade,
    project_id uuid references projects(id) on delete cascade,
    team_id uuid references teams(id) on delete set null,
    name varchar(160) not null,
    type varchar(40) not null,
    filter_config jsonb not null default '{}'::jsonb,
    active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version bigint not null default 0,
    constraint ck_boards_type check (type in ('scrum', 'kanban', 'portfolio'))
);

create table board_columns (
    id uuid primary key default gen_random_uuid(),
    board_id uuid not null references boards(id) on delete cascade,
    name varchar(120) not null,
    status_ids jsonb not null default '[]'::jsonb,
    position integer not null default 0,
    wip_limit integer,
    done_column boolean not null default false
);

create table board_swimlanes (
    id uuid primary key default gen_random_uuid(),
    board_id uuid not null references boards(id) on delete cascade,
    name varchar(120) not null,
    swimlane_type varchar(80) not null,
    query jsonb not null default '{}'::jsonb,
    position integer not null default 0,
    enabled boolean not null default true
);

create table iterations (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references workspaces(id) on delete cascade,
    project_id uuid references projects(id) on delete cascade,
    team_id uuid references teams(id) on delete set null,
    name varchar(160) not null,
    start_date date,
    end_date date,
    status varchar(40) not null default 'planned',
    committed_points numeric(12, 2),
    completed_points numeric(12, 2),
    constraint ck_iterations_status check (status in ('planned', 'active', 'closed', 'cancelled'))
);

create table iteration_work_items (
    iteration_id uuid not null references iterations(id) on delete cascade,
    work_item_id uuid not null references work_items(id) on delete cascade,
    added_by_id uuid references users(id) on delete set null,
    added_at timestamptz not null default now(),
    removed_at timestamptz,
    primary key (iteration_id, work_item_id)
);

create table releases (
    id uuid primary key default gen_random_uuid(),
    project_id uuid not null references projects(id) on delete cascade,
    name varchar(160) not null,
    version varchar(80),
    start_date date,
    release_date date,
    status varchar(40) not null default 'planned',
    description text,
    unique (project_id, name)
);

create table release_work_items (
    release_id uuid not null references releases(id) on delete cascade,
    work_item_id uuid not null references work_items(id) on delete cascade,
    added_by_id uuid references users(id) on delete set null,
    added_at timestamptz not null default now(),
    primary key (release_id, work_item_id)
);

create table roadmaps (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references workspaces(id) on delete cascade,
    project_id uuid references projects(id) on delete cascade,
    name varchar(160) not null,
    config jsonb not null default '{}'::jsonb,
    owner_id uuid references users(id) on delete set null,
    visibility varchar(40) not null default 'workspace',
    constraint ck_roadmaps_visibility check (visibility in ('private', 'workspace', 'public'))
);

create table roadmap_items (
    id uuid primary key default gen_random_uuid(),
    roadmap_id uuid not null references roadmaps(id) on delete cascade,
    work_item_id uuid not null references work_items(id) on delete cascade,
    start_date date,
    end_date date,
    position integer not null default 0,
    display_config jsonb not null default '{}'::jsonb,
    unique (roadmap_id, work_item_id)
);

create table project_settings (
    project_id uuid primary key references projects(id) on delete cascade,
    default_workflow_id uuid references workflows(id) on delete set null,
    default_board_id uuid references boards(id) on delete set null,
    estimation_unit varchar(40) not null default 'points',
    cross_project_linking_policy varchar(40) not null default 'links_only',
    config jsonb not null default '{}'::jsonb,
    updated_at timestamptz not null default now(),
    constraint ck_project_settings_cross_project_linking_policy check (
        cross_project_linking_policy in ('links_only', 'disabled')
    )
);

create table custom_fields (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references workspaces(id) on delete cascade,
    name varchar(160) not null,
    key varchar(120) not null,
    field_type varchar(80) not null,
    options jsonb not null default '{}'::jsonb,
    searchable boolean not null default false,
    archived boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version bigint not null default 0,
    unique (workspace_id, key)
);

create table custom_field_contexts (
    id uuid primary key default gen_random_uuid(),
    custom_field_id uuid not null references custom_fields(id) on delete cascade,
    project_id uuid references projects(id) on delete cascade,
    work_item_type_id uuid references work_item_types(id) on delete cascade,
    required boolean not null default false,
    default_value jsonb,
    validation_config jsonb not null default '{}'::jsonb
);

create table custom_field_values (
    id uuid primary key default gen_random_uuid(),
    work_item_id uuid not null references work_items(id) on delete cascade,
    custom_field_id uuid not null references custom_fields(id) on delete cascade,
    value jsonb,
    updated_at timestamptz not null default now(),
    unique (work_item_id, custom_field_id)
);

create table screens (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references workspaces(id) on delete cascade,
    name varchar(160) not null,
    screen_type varchar(80) not null,
    config jsonb not null default '{}'::jsonb
);

create table screen_fields (
    id uuid primary key default gen_random_uuid(),
    screen_id uuid not null references screens(id) on delete cascade,
    custom_field_id uuid references custom_fields(id) on delete cascade,
    system_field_key varchar(120),
    position integer not null default 0,
    required boolean not null default false,
    constraint ck_screen_fields_one_field check (
        (custom_field_id is not null and system_field_key is null)
        or (custom_field_id is null and system_field_key is not null)
    )
);

create table screen_assignments (
    id uuid primary key default gen_random_uuid(),
    screen_id uuid not null references screens(id) on delete cascade,
    project_id uuid references projects(id) on delete cascade,
    work_item_type_id uuid references work_item_types(id) on delete cascade,
    operation varchar(40) not null,
    priority integer not null default 0,
    constraint ck_screen_assignments_operation check (operation in ('create', 'edit', 'view'))
);

create table field_configurations (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references workspaces(id) on delete cascade,
    custom_field_id uuid not null references custom_fields(id) on delete cascade,
    project_id uuid references projects(id) on delete cascade,
    work_item_type_id uuid references work_item_types(id) on delete cascade,
    required boolean not null default false,
    hidden boolean not null default false,
    default_value jsonb,
    validation_config jsonb not null default '{}'::jsonb
);

create table comments (
    id uuid primary key default gen_random_uuid(),
    work_item_id uuid not null references work_items(id) on delete cascade,
    author_id uuid references users(id) on delete set null,
    body_markdown text,
    body_document jsonb,
    visibility varchar(40) not null default 'workspace',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    constraint ck_comments_visibility check (visibility in ('workspace', 'public', 'private'))
);

create table work_logs (
    id uuid primary key default gen_random_uuid(),
    work_item_id uuid not null references work_items(id) on delete cascade,
    user_id uuid references users(id) on delete set null,
    minutes_spent integer not null,
    work_date date not null,
    started_at timestamptz,
    description_markdown text,
    description_document jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    constraint ck_work_logs_minutes_spent check (minutes_spent > 0)
);

create table attachment_storage_configs (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid references workspaces(id) on delete cascade,
    name varchar(160) not null,
    provider varchar(80) not null,
    config jsonb not null default '{}'::jsonb,
    active boolean not null default true,
    default_config boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table attachments (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references workspaces(id) on delete cascade,
    storage_config_id uuid references attachment_storage_configs(id) on delete set null,
    uploader_id uuid references users(id) on delete set null,
    filename varchar(500) not null,
    content_type varchar(255),
    storage_key text not null,
    size_bytes bigint not null,
    checksum varchar(255),
    visibility varchar(40) not null default 'restricted',
    created_at timestamptz not null default now(),
    constraint ck_attachments_size check (size_bytes >= 0),
    constraint ck_attachments_visibility check (visibility in ('restricted', 'public'))
);

create table work_item_attachments (
    work_item_id uuid not null references work_items(id) on delete cascade,
    attachment_id uuid not null references attachments(id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (work_item_id, attachment_id)
);

create table watchers (
    work_item_id uuid not null references work_items(id) on delete cascade,
    user_id uuid not null references users(id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (work_item_id, user_id)
);

create table mentions (
    id uuid primary key default gen_random_uuid(),
    source_type varchar(80) not null,
    source_id uuid not null,
    mentioned_user_id uuid not null references users(id) on delete cascade,
    created_at timestamptz not null default now()
);

create table activity_events (
    id uuid primary key default gen_random_uuid(),
    domain_event_id uuid,
    workspace_id uuid not null references workspaces(id) on delete cascade,
    actor_id uuid references users(id) on delete set null,
    entity_type varchar(80) not null,
    entity_id uuid not null,
    event_type varchar(120) not null,
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    unique (domain_event_id, entity_type, entity_id)
);

create table audit_log_entries (
    id uuid primary key default gen_random_uuid(),
    domain_event_id uuid,
    workspace_id uuid not null references workspaces(id) on delete cascade,
    actor_id uuid references users(id) on delete set null,
    action varchar(120) not null,
    target_type varchar(80) not null,
    target_id uuid,
    before_value jsonb,
    after_value jsonb,
    ip_address varchar(80),
    user_agent text,
    created_at timestamptz not null default now(),
    unique (domain_event_id, workspace_id, action)
);

create table domain_events (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid references workspaces(id) on delete cascade,
    aggregate_type varchar(80) not null,
    aggregate_id uuid not null,
    event_type varchar(120) not null,
    payload jsonb not null default '{}'::jsonb,
    processing_status varchar(40) not null default 'pending',
    attempts integer not null default 0,
    last_error text,
    occurred_at timestamptz not null default now(),
    published_at timestamptz
);

create table domain_event_deliveries (
    id uuid primary key default gen_random_uuid(),
    domain_event_id uuid not null references domain_events(id) on delete cascade,
    consumer_key varchar(160) not null,
    delivery_status varchar(40) not null default 'pending',
    attempts integer not null default 0,
    last_error text,
    next_attempt_at timestamptz,
    delivered_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (domain_event_id, consumer_key),
    constraint ck_domain_event_deliveries_status check (delivery_status in ('pending', 'processing', 'delivered', 'failed', 'dead_lettered'))
);

create table event_consumer_configs (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid references workspaces(id) on delete cascade,
    consumer_key varchar(160) not null unique,
    consumer_type varchar(80) not null,
    display_name varchar(160) not null,
    event_types jsonb not null default '[]'::jsonb,
    config jsonb not null default '{}'::jsonb,
    enabled boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table audit_retention_policies (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null unique references workspaces(id) on delete cascade,
    retention_enabled boolean not null default false,
    retention_days integer,
    updated_by_id uuid references users(id) on delete set null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_audit_retention_days_positive check (retention_days is null or retention_days > 0),
    constraint ck_audit_retention_enabled_days check (retention_enabled = false or retention_days is not null)
);

create table work_item_status_history (
    id uuid primary key default gen_random_uuid(),
    work_item_id uuid not null references work_items(id) on delete cascade,
    from_status_id uuid references workflow_statuses(id) on delete set null,
    to_status_id uuid not null references workflow_statuses(id) on delete restrict,
    changed_by_id uuid references users(id) on delete set null,
    changed_at timestamptz not null default now()
);

create table work_item_assignment_history (
    id uuid primary key default gen_random_uuid(),
    work_item_id uuid not null references work_items(id) on delete cascade,
    from_user_id uuid references users(id) on delete set null,
    to_user_id uuid references users(id) on delete set null,
    changed_by_id uuid references users(id) on delete set null,
    changed_at timestamptz not null default now()
);

create table work_item_team_history (
    id uuid primary key default gen_random_uuid(),
    work_item_id uuid not null references work_items(id) on delete cascade,
    from_team_id uuid references teams(id) on delete set null,
    to_team_id uuid references teams(id) on delete set null,
    changed_by_id uuid references users(id) on delete set null,
    changed_at timestamptz not null default now()
);

create table work_item_estimate_history (
    id uuid primary key default gen_random_uuid(),
    work_item_id uuid not null references work_items(id) on delete cascade,
    estimate_type varchar(40) not null,
    old_value numeric(12, 2),
    new_value numeric(12, 2),
    changed_by_id uuid references users(id) on delete set null,
    changed_at timestamptz not null default now()
);

create table iteration_snapshots (
    id uuid primary key default gen_random_uuid(),
    iteration_id uuid not null references iterations(id) on delete cascade,
    snapshot_date date not null,
    committed_points numeric(12, 2) not null default 0,
    completed_points numeric(12, 2) not null default 0,
    remaining_points numeric(12, 2) not null default 0,
    scope_added_points numeric(12, 2) not null default 0,
    scope_removed_points numeric(12, 2) not null default 0,
    unique (iteration_id, snapshot_date)
);

create table cumulative_flow_snapshots (
    id uuid primary key default gen_random_uuid(),
    board_id uuid not null references boards(id) on delete cascade,
    snapshot_date date not null,
    status_id uuid not null references workflow_statuses(id) on delete cascade,
    work_item_count integer not null default 0,
    total_points numeric(12, 2) not null default 0,
    unique (board_id, snapshot_date, status_id)
);

create table velocity_snapshots (
    id uuid primary key default gen_random_uuid(),
    team_id uuid not null references teams(id) on delete cascade,
    iteration_id uuid not null references iterations(id) on delete cascade,
    committed_points numeric(12, 2) not null default 0,
    completed_points numeric(12, 2) not null default 0,
    carried_over_points numeric(12, 2) not null default 0,
    unique (team_id, iteration_id)
);

create table cycle_time_records (
    id uuid primary key default gen_random_uuid(),
    work_item_id uuid not null references work_items(id) on delete cascade,
    created_at timestamptz not null default now(),
    started_at timestamptz,
    completed_at timestamptz,
    lead_time_minutes integer,
    cycle_time_minutes integer,
    unique (work_item_id)
);

create table notification_preferences (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references users(id) on delete cascade,
    workspace_id uuid references workspaces(id) on delete cascade,
    channel varchar(80) not null,
    event_type varchar(120) not null,
    enabled boolean not null default true,
    config jsonb not null default '{}'::jsonb,
    unique (user_id, workspace_id, channel, event_type)
);

create table notifications (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references users(id) on delete cascade,
    actor_id uuid references users(id) on delete set null,
    workspace_id uuid references workspaces(id) on delete cascade,
    type varchar(120) not null,
    title varchar(255) not null,
    body text,
    target_type varchar(80),
    target_id uuid,
    read_at timestamptz,
    created_at timestamptz not null default now()
);

create table automation_rules (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references workspaces(id) on delete cascade,
    project_id uuid references projects(id) on delete cascade,
    name varchar(160) not null,
    trigger_type varchar(120) not null,
    trigger_config jsonb not null default '{}'::jsonb,
    enabled boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table automation_conditions (
    id uuid primary key default gen_random_uuid(),
    rule_id uuid not null references automation_rules(id) on delete cascade,
    condition_type varchar(120) not null,
    config jsonb not null default '{}'::jsonb,
    position integer not null default 0
);

create table automation_actions (
    id uuid primary key default gen_random_uuid(),
    rule_id uuid not null references automation_rules(id) on delete cascade,
    action_type varchar(120) not null,
    execution_mode varchar(40) not null default 'sync',
    config jsonb not null default '{}'::jsonb,
    position integer not null default 0,
    constraint ck_automation_actions_execution_mode check (execution_mode in ('sync', 'async', 'hybrid'))
);

create table automation_execution_jobs (
    id uuid primary key default gen_random_uuid(),
    rule_id uuid not null references automation_rules(id) on delete cascade,
    workspace_id uuid not null references workspaces(id) on delete cascade,
    source_entity_type varchar(80),
    source_entity_id uuid,
    status varchar(40) not null default 'queued',
    payload jsonb not null default '{}'::jsonb,
    attempts integer not null default 0,
    next_attempt_at timestamptz,
    started_at timestamptz,
    completed_at timestamptz,
    failed_at timestamptz,
    last_error text,
    created_at timestamptz not null default now(),
    constraint ck_automation_execution_jobs_status check (status in ('queued', 'running', 'succeeded', 'failed', 'cancelled'))
);

create table automation_execution_logs (
    id uuid primary key default gen_random_uuid(),
    job_id uuid not null references automation_execution_jobs(id) on delete cascade,
    action_id uuid references automation_actions(id) on delete set null,
    status varchar(40) not null,
    message text,
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);

create table webhooks (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references workspaces(id) on delete cascade,
    name varchar(160) not null,
    url text not null,
    secret_hash text,
    event_types jsonb not null default '[]'::jsonb,
    enabled boolean not null default true
);

create table webhook_deliveries (
    id uuid primary key default gen_random_uuid(),
    webhook_id uuid not null references webhooks(id) on delete cascade,
    event_type varchar(120) not null,
    payload jsonb not null,
    status varchar(40) not null default 'queued',
    response_code integer,
    response_body text,
    attempt_count integer not null default 0,
    next_retry_at timestamptz,
    created_at timestamptz not null default now(),
    constraint ck_webhook_deliveries_status check (status in ('queued', 'delivered', 'failed', 'cancelled'))
);

create table agent_providers (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references workspaces(id) on delete cascade,
    provider_key varchar(80) not null,
    provider_type varchar(80) not null,
    display_name varchar(160) not null,
    dispatch_mode varchar(40) not null default 'manual',
    callback_url text,
    capability_schema jsonb not null default '{}'::jsonb,
    config jsonb not null default '{}'::jsonb,
    enabled boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_agent_providers_dispatch_mode check (
        dispatch_mode in ('webhook_push', 'polling', 'managed', 'manual')
    ),
    unique (workspace_id, provider_key)
);

create table agent_provider_credentials (
    id uuid primary key default gen_random_uuid(),
    provider_id uuid not null references agent_providers(id) on delete cascade,
    credential_type varchar(80) not null,
    encrypted_secret text not null,
    metadata jsonb not null default '{}'::jsonb,
    active boolean not null default true,
    expires_at timestamptz,
    created_at timestamptz not null default now(),
    rotated_at timestamptz
);

create table agent_profiles (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references workspaces(id) on delete cascade,
    user_id uuid not null references users(id) on delete cascade,
    provider_id uuid not null references agent_providers(id) on delete cascade,
    display_name varchar(160) not null,
    status varchar(40) not null default 'active',
    max_concurrent_tasks integer not null default 1,
    capabilities jsonb not null default '{}'::jsonb,
    config jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_agent_profiles_status check (status in ('active', 'paused', 'disabled')),
    constraint ck_agent_profiles_max_concurrent_tasks check (max_concurrent_tasks > 0),
    unique (workspace_id, user_id),
    unique (provider_id, user_id)
);

create table agent_profile_projects (
    agent_profile_id uuid not null references agent_profiles(id) on delete cascade,
    project_id uuid not null references projects(id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (agent_profile_id, project_id)
);

create table repository_connections (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references workspaces(id) on delete cascade,
    project_id uuid references projects(id) on delete cascade,
    provider varchar(80) not null,
    name varchar(160) not null,
    repository_url text not null,
    default_branch varchar(255) not null default 'main',
    config jsonb not null default '{}'::jsonb,
    active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (workspace_id, project_id, name)
);

create table agent_tasks (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references workspaces(id) on delete cascade,
    work_item_id uuid not null references work_items(id) on delete cascade,
    agent_profile_id uuid not null references agent_profiles(id) on delete restrict,
    provider_id uuid not null references agent_providers(id) on delete restrict,
    requested_by_id uuid references users(id) on delete set null,
    status varchar(40) not null default 'queued',
    dispatch_mode varchar(40) not null default 'manual',
    external_task_id varchar(255),
    context_snapshot jsonb not null default '{}'::jsonb,
    request_payload jsonb not null default '{}'::jsonb,
    result_payload jsonb,
    queued_at timestamptz not null default now(),
    started_at timestamptz,
    completed_at timestamptz,
    failed_at timestamptz,
    canceled_at timestamptz,
    constraint ck_agent_tasks_status check (
        status in ('queued', 'running', 'waiting_for_input', 'review_requested', 'completed', 'failed', 'canceled')
    ),
    constraint ck_agent_tasks_dispatch_mode check (
        dispatch_mode in ('webhook_push', 'polling', 'managed', 'manual')
    )
);

create table agent_task_events (
    id uuid primary key default gen_random_uuid(),
    agent_task_id uuid not null references agent_tasks(id) on delete cascade,
    event_type varchar(120) not null,
    severity varchar(40) not null default 'info',
    message text,
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    constraint ck_agent_task_events_severity check (severity in ('debug', 'info', 'warning', 'error'))
);

create table agent_messages (
    id uuid primary key default gen_random_uuid(),
    agent_task_id uuid not null references agent_tasks(id) on delete cascade,
    sender_user_id uuid references users(id) on delete set null,
    sender_type varchar(40) not null,
    body_markdown text,
    body_document jsonb,
    created_at timestamptz not null default now(),
    constraint ck_agent_messages_sender_type check (sender_type in ('human', 'agent', 'system'))
);

create table agent_artifacts (
    id uuid primary key default gen_random_uuid(),
    agent_task_id uuid not null references agent_tasks(id) on delete cascade,
    artifact_type varchar(80) not null,
    name varchar(255) not null,
    external_url text,
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);

create table agent_task_repositories (
    id uuid primary key default gen_random_uuid(),
    agent_task_id uuid not null references agent_tasks(id) on delete cascade,
    repository_connection_id uuid not null references repository_connections(id) on delete restrict,
    base_branch varchar(255),
    working_branch varchar(255),
    pull_request_url text,
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    unique (agent_task_id, repository_connection_id)
);

create table external_integrations (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references workspaces(id) on delete cascade,
    provider varchar(80) not null,
    status varchar(40) not null default 'active',
    config jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table external_identities (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references users(id) on delete cascade,
    provider varchar(80) not null,
    external_user_id varchar(255) not null,
    external_username varchar(160),
    created_at timestamptz not null default now(),
    unique (provider, external_user_id)
);

create table external_references (
    id uuid primary key default gen_random_uuid(),
    integration_id uuid references external_integrations(id) on delete cascade,
    entity_type varchar(80) not null,
    entity_id uuid not null,
    provider varchar(80) not null,
    external_id varchar(255) not null,
    external_url text,
    metadata jsonb not null default '{}'::jsonb,
    unique (provider, external_id, entity_type)
);

create table import_jobs (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references workspaces(id) on delete cascade,
    requested_by_id uuid references users(id) on delete set null,
    provider varchar(80) not null,
    status varchar(40) not null default 'queued',
    config jsonb not null default '{}'::jsonb,
    started_at timestamptz,
    finished_at timestamptz,
    constraint ck_import_jobs_status check (status in ('queued', 'running', 'completed', 'failed', 'cancelled'))
);

create table import_job_records (
    id uuid primary key default gen_random_uuid(),
    import_job_id uuid not null references import_jobs(id) on delete cascade,
    source_type varchar(80) not null,
    source_id varchar(255) not null,
    target_type varchar(80),
    target_id uuid,
    status varchar(40) not null default 'pending',
    error_message text,
    raw_payload jsonb,
    unique (import_job_id, source_type, source_id)
);

create table export_jobs (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references workspaces(id) on delete cascade,
    requested_by_id uuid references users(id) on delete set null,
    export_type varchar(80) not null,
    status varchar(40) not null default 'queued',
    file_attachment_id uuid references attachments(id) on delete set null,
    started_at timestamptz,
    finished_at timestamptz,
    constraint ck_export_jobs_status check (status in ('queued', 'running', 'completed', 'failed', 'cancelled'))
);

create table saved_filters (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references workspaces(id) on delete cascade,
    owner_id uuid references users(id) on delete set null,
    project_id uuid references projects(id) on delete cascade,
    team_id uuid references teams(id) on delete set null,
    name varchar(160) not null,
    query jsonb not null default '{}'::jsonb,
    visibility varchar(40) not null default 'private',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_saved_filters_visibility check (visibility in ('private', 'team', 'project', 'workspace', 'public')),
    constraint ck_saved_filters_team_visibility check (
        (visibility = 'team' and team_id is not null)
        or (visibility <> 'team' and team_id is null)
    ),
    constraint ck_saved_filters_project_visibility check (
        (visibility = 'project' and project_id is not null)
        or (visibility <> 'project' and project_id is null)
    )
);

create table dashboards (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references workspaces(id) on delete cascade,
    owner_id uuid references users(id) on delete set null,
    project_id uuid references projects(id) on delete cascade,
    team_id uuid references teams(id) on delete set null,
    name varchar(160) not null,
    visibility varchar(40) not null default 'private',
    layout jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_dashboards_visibility check (visibility in ('private', 'team', 'project', 'workspace', 'public')),
    constraint ck_dashboards_team_visibility check (
        (visibility = 'team' and team_id is not null)
        or (visibility <> 'team' and team_id is null)
    ),
    constraint ck_dashboards_project_visibility check (
        (visibility = 'project' and project_id is not null)
        or (visibility <> 'project' and project_id is null)
    )
);

create table dashboard_widgets (
    id uuid primary key default gen_random_uuid(),
    dashboard_id uuid not null references dashboards(id) on delete cascade,
    widget_type varchar(120) not null,
    title varchar(160),
    config jsonb not null default '{}'::jsonb,
    position_x integer not null default 0,
    position_y integer not null default 0,
    width integer not null default 1,
    height integer not null default 1,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table report_query_catalog (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references workspaces(id) on delete cascade,
    owner_id uuid references users(id) on delete set null,
    project_id uuid references projects(id) on delete cascade,
    team_id uuid references teams(id) on delete set null,
    query_key varchar(120) not null,
    name varchar(160) not null,
    description text,
    query_type varchar(80) not null,
    query_config jsonb not null default '{}'::jsonb,
    parameters_schema jsonb not null default '{}'::jsonb,
    visibility varchar(40) not null default 'private',
    enabled boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (workspace_id, query_key),
    constraint ck_report_query_catalog_visibility check (visibility in ('private', 'team', 'project', 'workspace', 'public')),
    constraint ck_report_query_catalog_team_visibility check (
        (visibility = 'team' and team_id is not null)
        or (visibility <> 'team' and team_id is null)
    ),
    constraint ck_report_query_catalog_project_visibility check (
        (visibility = 'project' and project_id is not null)
        or (visibility <> 'project' and project_id is null)
    )
);

create table views (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references workspaces(id) on delete cascade,
    owner_id uuid references users(id) on delete set null,
    name varchar(160) not null,
    view_type varchar(80) not null,
    config jsonb not null default '{}'::jsonb,
    visibility varchar(40) not null default 'private',
    constraint ck_views_visibility check (visibility in ('private', 'workspace', 'public'))
);

create table favorites (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references users(id) on delete cascade,
    entity_type varchar(80) not null,
    entity_id uuid not null,
    created_at timestamptz not null default now(),
    unique (user_id, entity_type, entity_id)
);

create table recent_items (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references users(id) on delete cascade,
    entity_type varchar(80) not null,
    entity_id uuid not null,
    viewed_at timestamptz not null default now(),
    unique (user_id, entity_type, entity_id)
);

insert into permissions (key, name, description, category) values
    ('workspace.admin', 'Administer workspace', 'Manage workspace settings, roles, and configuration.', 'workspace'),
    ('workspace.read', 'Read workspace', 'View workspace-level data.', 'workspace'),
    ('user.manage', 'Manage users', 'Invite users, create users, and manage workspace membership.', 'identity'),
    ('project.create', 'Create projects', 'Create projects in a workspace.', 'project'),
    ('project.admin', 'Administer project', 'Manage project settings and membership.', 'project'),
    ('project.read', 'Read project', 'View project data.', 'project'),
    ('work_item.create', 'Create work items', 'Create work items.', 'work_item'),
    ('work_item.read', 'Read work items', 'View work items.', 'work_item'),
    ('work_item.update', 'Update work items', 'Edit work items.', 'work_item'),
    ('work_item.delete', 'Delete work items', 'Delete or archive work items.', 'work_item'),
    ('work_item.transition', 'Transition work items', 'Move work items through workflow transitions.', 'work_item'),
    ('work_item.comment', 'Comment on work items', 'Create comments on work items.', 'work_item'),
    ('work_item.link', 'Link work items', 'Create and remove work item links.', 'work_item'),
    ('work_log.create_own', 'Create own work logs', 'Log time for yourself on readable work items.', 'work_log'),
    ('work_log.update_own', 'Update own work logs', 'Edit your own time entries.', 'work_log'),
    ('work_log.delete_own', 'Delete own work logs', 'Delete your own time entries.', 'work_log'),
    ('workflow.admin', 'Administer workflows', 'Manage workflows, statuses, and transitions.', 'workflow'),
    ('board.admin', 'Administer boards', 'Manage board configuration.', 'planning'),
    ('automation.admin', 'Administer automation', 'Manage automation rules and webhooks.', 'automation'),
    ('agent.provider.manage', 'Manage agent providers', 'Create and update AI agent provider configuration.', 'agent'),
    ('agent.provider.credential.manage', 'Manage agent provider credentials', 'Create, rotate, and disable AI agent provider credentials.', 'agent'),
    ('agent.profile.manage', 'Manage agent profiles', 'Create and update assignable AI agent profiles.', 'agent'),
    ('agent.assign', 'Assign agents', 'Assign work items to AI agents.', 'agent'),
    ('agent.task.view', 'View agent tasks', 'View AI agent task details.', 'agent'),
    ('agent.task.cancel', 'Cancel agent tasks', 'Cancel running or queued AI agent tasks.', 'agent'),
    ('agent.task.retry', 'Retry agent tasks', 'Retry failed or canceled AI agent tasks.', 'agent'),
    ('agent.task.view_logs', 'View agent task logs', 'View AI agent task events and diagnostic details.', 'agent'),
    ('agent.task.accept_result', 'Accept agent task results', 'Accept AI agent results and continue workflow.', 'agent'),
    ('repository_connection.manage', 'Manage repository connections', 'Create and update source repository connections.', 'agent'),
    ('report.read', 'Read reports', 'View reports and dashboards.', 'reporting'),
    ('report.manage', 'Manage reports', 'Create and manage shared dashboards and report configuration.', 'reporting')
on conflict (key) do nothing;

create or replace function set_updated_at()
returns trigger as $$
begin
    new.updated_at = now();
    return new;
end;
$$ language plpgsql;

create or replace function enforce_project_workspace_consistency()
returns trigger as $$
declare
    project_workspace_id uuid;
begin
    select workspace_id into project_workspace_id from projects where id = new.project_id;

    if project_workspace_id is null then
        raise exception 'Project % does not exist', new.project_id;
    end if;

    if new.workspace_id <> project_workspace_id then
        raise exception 'Work item workspace % does not match project workspace %', new.workspace_id, project_workspace_id;
    end if;

    return new;
end;
$$ language plpgsql;

create or replace function enforce_work_item_parent_same_project()
returns trigger as $$
declare
    parent_project_id uuid;
begin
    if new.parent_id is null then
        return new;
    end if;

    select project_id into parent_project_id from work_items where id = new.parent_id;

    if parent_project_id is null then
        raise exception 'Parent work item % does not exist', new.parent_id;
    end if;

    if parent_project_id <> new.project_id then
        raise exception 'Structural parentage across projects is not allowed. Use work_item_links instead.';
    end if;

    return new;
end;
$$ language plpgsql;

create or replace function prevent_work_item_parent_cycle()
returns trigger as $$
declare
    found_cycle uuid;
begin
    if new.parent_id is null then
        return new;
    end if;

    with recursive ancestors(id) as (
        select new.parent_id
        union all
        select wi.parent_id
        from work_items wi
        join ancestors a on wi.id = a.id
        where wi.parent_id is not null
    )
    select id into found_cycle from ancestors where id = new.id limit 1;

    if found_cycle is not null then
        raise exception 'Work item parent cycle detected for %', new.id;
    end if;

    return new;
end;
$$ language plpgsql;

create trigger trg_work_items_project_workspace
before insert or update of workspace_id, project_id on work_items
for each row execute function enforce_project_workspace_consistency();

create trigger trg_work_items_parent_same_project
before insert or update of parent_id, project_id on work_items
for each row execute function enforce_work_item_parent_same_project();

create trigger trg_work_items_no_parent_cycle
before insert or update of parent_id on work_items
for each row execute function prevent_work_item_parent_cycle();

create trigger trg_users_updated_at before update on users for each row execute function set_updated_at();
create trigger trg_user_auth_identities_updated_at before update on user_auth_identities for each row execute function set_updated_at();
create trigger trg_user_invitations_updated_at before update on user_invitations for each row execute function set_updated_at();
create trigger trg_api_tokens_updated_at before update on api_tokens for each row execute function set_updated_at();
create trigger trg_organizations_updated_at before update on organizations for each row execute function set_updated_at();
create trigger trg_workspaces_updated_at before update on workspaces for each row execute function set_updated_at();
create trigger trg_projects_updated_at before update on projects for each row execute function set_updated_at();
create trigger trg_workspace_work_item_sequences_updated_at before update on workspace_work_item_sequences for each row execute function set_updated_at();
create trigger trg_project_work_item_sequences_updated_at before update on project_work_item_sequences for each row execute function set_updated_at();
create trigger trg_programs_updated_at before update on programs for each row execute function set_updated_at();
create trigger trg_teams_updated_at before update on teams for each row execute function set_updated_at();
create trigger trg_roles_updated_at before update on roles for each row execute function set_updated_at();
create trigger trg_project_memberships_updated_at before update on project_memberships for each row execute function set_updated_at();
create trigger trg_work_item_types_updated_at before update on work_item_types for each row execute function set_updated_at();
create trigger trg_workflows_updated_at before update on workflows for each row execute function set_updated_at();
create trigger trg_boards_updated_at before update on boards for each row execute function set_updated_at();
create trigger trg_work_items_updated_at before update on work_items for each row execute function set_updated_at();
create trigger trg_custom_fields_updated_at before update on custom_fields for each row execute function set_updated_at();
create trigger trg_comments_updated_at before update on comments for each row execute function set_updated_at();
create trigger trg_work_logs_updated_at before update on work_logs for each row execute function set_updated_at();
create trigger trg_attachment_storage_configs_updated_at before update on attachment_storage_configs for each row execute function set_updated_at();
create trigger trg_external_integrations_updated_at before update on external_integrations for each row execute function set_updated_at();
create trigger trg_automation_rules_updated_at before update on automation_rules for each row execute function set_updated_at();
create trigger trg_agent_providers_updated_at before update on agent_providers for each row execute function set_updated_at();
create trigger trg_agent_profiles_updated_at before update on agent_profiles for each row execute function set_updated_at();
create trigger trg_repository_connections_updated_at before update on repository_connections for each row execute function set_updated_at();
create trigger trg_domain_event_deliveries_updated_at before update on domain_event_deliveries for each row execute function set_updated_at();
create trigger trg_event_consumer_configs_updated_at before update on event_consumer_configs for each row execute function set_updated_at();
create trigger trg_audit_retention_policies_updated_at before update on audit_retention_policies for each row execute function set_updated_at();
create trigger trg_saved_filters_updated_at before update on saved_filters for each row execute function set_updated_at();
create trigger trg_dashboards_updated_at before update on dashboards for each row execute function set_updated_at();
create trigger trg_dashboard_widgets_updated_at before update on dashboard_widgets for each row execute function set_updated_at();
create trigger trg_report_query_catalog_updated_at before update on report_query_catalog for each row execute function set_updated_at();

create index ix_users_account_type on users(account_type);
create index ix_workspaces_organization_key on workspaces(organization_id, key);
create index ix_workspace_memberships_workspace_user on workspace_memberships(workspace_id, user_id);
create index ix_project_memberships_project_user on project_memberships(project_id, user_id);
create index ix_projects_workspace_key on projects(workspace_id, key);
create index ix_projects_workspace_parent on projects(workspace_id, parent_project_id);
create index ix_program_projects_project on program_projects(project_id);
create index ix_team_memberships_user on team_memberships(user_id);
create index ix_project_teams_team on project_teams(team_id);
create index ix_roles_workspace on roles(workspace_id);
create index ix_roles_project on roles(project_id);
create index ix_work_item_types_workspace_key on work_item_types(workspace_id, key);
create index ix_work_item_type_rules_workspace_parent_child on work_item_type_rules(workspace_id, parent_type_id, child_type_id);
create index ix_project_work_item_types_project_type on project_work_item_types(project_id, work_item_type_id);
create index ix_workflow_assignments_project_type on workflow_assignments(project_id, work_item_type_id);
create index ix_workflow_statuses_workflow_category on workflow_statuses(workflow_id, category);
create index ix_work_items_workspace_key on work_items(workspace_id, key);
create index ix_work_items_project_sequence on work_items(project_id, sequence_number);
create index ix_work_items_workspace_sequence on work_items(workspace_id, workspace_sequence_number);
create index ix_work_items_project_type on work_items(project_id, type_id);
create index ix_work_items_parent on work_items(parent_id);
create index ix_work_items_resolution on work_items(resolution_id);
create index ix_work_items_team on work_items(team_id);
create index ix_work_items_status on work_items(status_id);
create index ix_work_items_assignee on work_items(assignee_id);
create index ix_work_items_reporter on work_items(reporter_id);
create index ix_work_items_rank on work_items(rank);
create index ix_work_items_deleted_at on work_items(deleted_at);
create index ix_work_items_title_search on work_items using gin (to_tsvector('english', title));
create index ix_work_item_closure_ancestor_descendant on work_item_closure(ancestor_work_item_id, descendant_work_item_id);
create index ix_work_item_closure_descendant_depth on work_item_closure(descendant_work_item_id, depth);
create index ix_work_item_links_source_type on work_item_links(source_work_item_id, link_type);
create index ix_work_item_links_target_type on work_item_links(target_work_item_id, link_type);
create index ix_labels_workspace_name on labels(workspace_id, name);
create index ix_components_project_name on components(project_id, name);
create index ix_boards_project on boards(project_id);
create index ix_boards_team on boards(team_id);
create index ix_iterations_project on iterations(project_id);
create index ix_iterations_team on iterations(team_id);
create index ix_iteration_work_items_work_item on iteration_work_items(work_item_id);
create index ix_releases_project on releases(project_id);
create index ix_release_work_items_work_item on release_work_items(work_item_id);
create index ix_roadmaps_project on roadmaps(project_id);
create index ix_custom_field_values_work_item_field on custom_field_values(work_item_id, custom_field_id);
create index ix_screen_assignments_project_type_operation on screen_assignments(project_id, work_item_type_id, operation);
create index ix_comments_work_item_created_at on comments(work_item_id, created_at);
create index ix_work_logs_work_item_work_date on work_logs(work_item_id, work_date);
create index ix_work_logs_user_work_date on work_logs(user_id, work_date);
create index ix_attachments_workspace on attachments(workspace_id);
create index ix_activity_events_workspace_created_at on activity_events(workspace_id, created_at);
create index ix_activity_events_entity_created_at on activity_events(entity_type, entity_id, created_at);
create index ix_audit_log_entries_workspace_created_at on audit_log_entries(workspace_id, created_at);
create index ix_domain_events_workspace_occurred_at on domain_events(workspace_id, occurred_at);
create index ix_domain_events_aggregate on domain_events(aggregate_type, aggregate_id);
create index ix_domain_events_status_occurred_at on domain_events(processing_status, occurred_at);
create index ix_api_tokens_user_type on api_tokens(user_id, token_type);
create index ix_api_tokens_workspace_type on api_tokens(workspace_id, token_type);
create index ix_api_tokens_active on api_tokens(token_type, revoked_at, expires_at);
create index ix_domain_event_deliveries_event on domain_event_deliveries(domain_event_id, delivery_status);
create index ix_domain_event_deliveries_consumer on domain_event_deliveries(consumer_key, delivery_status);
create index ix_event_consumer_configs_workspace_type on event_consumer_configs(workspace_id, consumer_type);
create index ix_event_consumer_configs_enabled on event_consumer_configs(enabled, consumer_type);
create index ix_work_item_status_history_work_item_changed_at on work_item_status_history(work_item_id, changed_at);
create index ix_work_item_assignment_history_work_item_changed_at on work_item_assignment_history(work_item_id, changed_at);
create index ix_work_item_team_history_work_item_changed_at on work_item_team_history(work_item_id, changed_at);
create index ix_work_item_estimate_history_work_item_changed_at on work_item_estimate_history(work_item_id, changed_at);
create index ix_notifications_user_created_at on notifications(user_id, created_at);
create index ix_automation_execution_jobs_status_next on automation_execution_jobs(status, next_attempt_at);
create index ix_webhook_deliveries_status_next on webhook_deliveries(status, next_retry_at);
create index ix_agent_providers_workspace_key on agent_providers(workspace_id, provider_key);
create index ix_agent_profiles_workspace_user on agent_profiles(workspace_id, user_id);
create index ix_agent_tasks_workspace_status_queued_at on agent_tasks(workspace_id, status, queued_at);
create index ix_agent_tasks_work_item_queued_at on agent_tasks(work_item_id, queued_at);
create unique index ux_agent_tasks_provider_external on agent_tasks(provider_id, external_task_id) where external_task_id is not null;
create index ix_agent_task_events_task_created_at on agent_task_events(agent_task_id, created_at);
create index ix_agent_artifacts_task_type on agent_artifacts(agent_task_id, artifact_type);
create index ix_agent_profile_projects_project on agent_profile_projects(project_id);
create index ix_repository_connections_workspace_project on repository_connections(workspace_id, project_id);
create index ix_external_references_entity on external_references(entity_type, entity_id);
create index ix_import_job_records_job_status on import_job_records(import_job_id, status);
create index ix_saved_filters_workspace_owner on saved_filters(workspace_id, owner_id);
create index ix_saved_filters_workspace_visibility on saved_filters(workspace_id, visibility);
create index ix_saved_filters_project on saved_filters(project_id);
create index ix_saved_filters_team on saved_filters(team_id);
create index ix_dashboards_workspace_owner on dashboards(workspace_id, owner_id);
create index ix_dashboards_workspace_project on dashboards(workspace_id, project_id);
create index ix_dashboards_workspace_team on dashboards(workspace_id, team_id);
create index ix_dashboard_widgets_dashboard_position on dashboard_widgets(dashboard_id, position_y, position_x);
create index ix_report_query_catalog_workspace_visibility on report_query_catalog(workspace_id, visibility);
create index ix_report_query_catalog_project on report_query_catalog(project_id);
create index ix_report_query_catalog_team on report_query_catalog(team_id);
create index ix_views_workspace_owner on views(workspace_id, owner_id);
create index ix_recent_items_user_viewed_at on recent_items(user_id, viewed_at desc);
