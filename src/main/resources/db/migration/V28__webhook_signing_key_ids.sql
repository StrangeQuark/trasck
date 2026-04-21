alter table webhooks
    add column if not exists secret_key_id text;

update webhooks
set secret_key_id = 'whsec_' || replace(gen_random_uuid()::text, '-', '')
where secret_key_id is null
  and secret_encrypted is not null
  and btrim(secret_encrypted) <> '';

create unique index if not exists idx_webhooks_secret_key_id
    on webhooks(secret_key_id)
    where secret_key_id is not null;
