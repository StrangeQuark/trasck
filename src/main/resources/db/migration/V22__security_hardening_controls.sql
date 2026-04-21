insert into permissions (key, name, description, category) values
    ('system.admin', 'Administer system', 'Manage installation-wide controls and production-only administration surfaces.', 'system')
on conflict (key) do nothing;

create table system_admins (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references users(id) on delete cascade,
    active boolean not null default true,
    granted_by_id uuid references users(id) on delete set null,
    granted_at timestamptz not null default now(),
    revoked_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index ux_system_admins_user on system_admins (user_id);
create index ix_system_admins_active on system_admins (user_id) where active;

create trigger trg_system_admins_updated_at
    before update on system_admins
    for each row execute function set_updated_at();

create table security_rate_limit_attempts (
    id uuid primary key default gen_random_uuid(),
    attempt_key varchar(720) not null,
    realm varchar(80) not null,
    identifier varchar(360) not null,
    remote_address varchar(120),
    first_failure_at timestamptz not null,
    failure_count integer not null,
    locked_until timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ux_security_rate_limit_attempts_key unique (attempt_key),
    constraint ck_security_rate_limit_failure_count check (failure_count >= 0)
);

create index ix_security_rate_limit_attempts_realm_updated_at
    on security_rate_limit_attempts (realm, updated_at desc);
create index ix_security_rate_limit_attempts_locked_until
    on security_rate_limit_attempts (locked_until)
    where locked_until is not null;

create trigger trg_security_rate_limit_attempts_updated_at
    before update on security_rate_limit_attempts
    for each row execute function set_updated_at();

create table security_auth_failure_events (
    id uuid primary key default gen_random_uuid(),
    event_type varchar(120) not null,
    realm varchar(80) not null,
    identifier varchar(360) not null,
    remote_address varchar(120),
    reason varchar(240),
    created_at timestamptz not null default now()
);

create index ix_security_auth_failure_events_realm_created_at
    on security_auth_failure_events (realm, created_at desc);
