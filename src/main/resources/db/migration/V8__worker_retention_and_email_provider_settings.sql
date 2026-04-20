alter table automation_worker_settings
    add column worker_run_retention_enabled boolean not null default false,
    add column worker_run_retention_days integer,
    add column worker_run_export_before_prune boolean not null default true;

alter table automation_worker_settings
    add constraint ck_automation_worker_settings_retention check (
        worker_run_retention_days is null
        or worker_run_retention_days > 0
    );

create table email_provider_settings (
    workspace_id uuid primary key references workspaces(id) on delete cascade,
    provider varchar(80) not null default 'maildev',
    from_email varchar(320) not null default 'no-reply@trasck.local',
    smtp_host varchar(255),
    smtp_port integer,
    smtp_username varchar(320),
    smtp_password_encrypted text,
    smtp_start_tls_enabled boolean not null default true,
    smtp_auth_enabled boolean not null default true,
    active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_email_provider_settings_provider check (provider in ('maildev', 'smtp')),
    constraint ck_email_provider_settings_smtp_port check (smtp_port is null or smtp_port between 1 and 65535),
    constraint ck_email_provider_settings_smtp_required check (
        provider <> 'smtp'
        or (smtp_host is not null and smtp_port is not null)
    )
);

create trigger trg_email_provider_settings_updated_at
    before update on email_provider_settings
    for each row execute function set_updated_at();

create index ix_email_provider_settings_provider
    on email_provider_settings(provider, active);
