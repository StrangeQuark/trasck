alter table automation_worker_runs
    drop constraint ck_automation_worker_runs_worker_type;

alter table automation_worker_runs
    add constraint ck_automation_worker_runs_worker_type check (
        worker_type in ('automation', 'webhook', 'email', 'import_conflict_resolution')
    );

alter table automation_worker_health
    drop constraint ck_automation_worker_health_worker_type;

alter table automation_worker_health
    add constraint ck_automation_worker_health_worker_type check (
        worker_type in ('automation', 'webhook', 'email', 'import_conflict_resolution')
    );
