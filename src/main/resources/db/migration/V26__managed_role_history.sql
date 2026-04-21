alter table roles
    add column status varchar(40) not null default 'active',
    add column archived_at timestamptz;

alter table roles
    add constraint ck_roles_status check (status in ('active', 'archived'));

create table role_versions (
    id uuid primary key default gen_random_uuid(),
    role_id uuid not null references roles(id) on delete cascade,
    workspace_id uuid,
    project_id uuid,
    version_number integer not null,
    name varchar(120) not null,
    key varchar(80) not null,
    scope varchar(40) not null,
    description text,
    system_role boolean not null default false,
    status varchar(40) not null default 'active',
    permission_keys jsonb not null default '[]'::jsonb,
    change_type varchar(80) not null,
    change_note text,
    created_by_id uuid references users(id) on delete set null,
    created_at timestamptz not null default now(),
    unique (role_id, version_number)
);

create index ix_role_versions_role_created on role_versions(role_id, created_at desc);
