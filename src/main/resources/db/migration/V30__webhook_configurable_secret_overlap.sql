alter table webhooks
    add column if not exists previous_secret_overlap_seconds bigint;

alter table webhooks
    drop constraint if exists ck_webhooks_previous_secret_overlap_positive;

alter table webhooks
    add constraint ck_webhooks_previous_secret_overlap_positive
        check (previous_secret_overlap_seconds is null or previous_secret_overlap_seconds > 0);
