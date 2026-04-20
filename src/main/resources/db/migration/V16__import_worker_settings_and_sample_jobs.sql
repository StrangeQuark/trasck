alter table automation_worker_settings
    add column import_conflict_resolution_enabled boolean not null default false,
    add column import_conflict_resolution_limit integer not null default 10;

alter table automation_worker_settings
    drop constraint ck_automation_worker_settings_limits;

alter table automation_worker_settings
    add constraint ck_automation_worker_settings_limits check (
        automation_limit between 1 and 100
        and webhook_limit between 1 and 100
        and email_limit between 1 and 100
        and import_conflict_resolution_limit between 1 and 100
        and webhook_max_attempts between 1 and 20
        and email_max_attempts between 1 and 20
    );
