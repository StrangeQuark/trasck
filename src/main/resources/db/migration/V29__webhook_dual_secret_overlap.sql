alter table webhooks
    add column if not exists previous_secret_hash text,
    add column if not exists previous_secret_encrypted text,
    add column if not exists previous_secret_key_id text,
    add column if not exists secret_rotated_at timestamptz,
    add column if not exists previous_secret_expires_at timestamptz;

alter table webhook_deliveries
    add column if not exists signature_key_id text;

update webhook_deliveries delivery
set signature_key_id = webhook.secret_key_id
from webhooks webhook
where delivery.webhook_id = webhook.id
  and delivery.signature_key_id is null
  and webhook.secret_key_id is not null;

create index if not exists ix_webhooks_previous_secret_key_id
    on webhooks(previous_secret_key_id)
    where previous_secret_key_id is not null;

create index if not exists ix_webhook_deliveries_signature_key_id
    on webhook_deliveries(signature_key_id)
    where signature_key_id is not null;
