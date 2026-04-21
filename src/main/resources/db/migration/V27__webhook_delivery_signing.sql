alter table webhooks
    add column if not exists secret_encrypted text;

