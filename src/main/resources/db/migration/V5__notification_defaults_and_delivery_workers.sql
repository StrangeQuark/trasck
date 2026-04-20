alter table notification_preferences
    alter column user_id drop not null;

alter table notification_preferences
    drop constraint if exists notification_preferences_user_id_workspace_id_channel_event_type_key;

create unique index ux_notification_preferences_user_scope
    on notification_preferences(user_id, workspace_id, channel, event_type)
    where user_id is not null;

create unique index ux_notification_preferences_workspace_default
    on notification_preferences(workspace_id, channel, event_type)
    where user_id is null;

alter table webhook_deliveries
    drop constraint if exists ck_webhook_deliveries_status;

alter table webhook_deliveries
    add constraint ck_webhook_deliveries_status
        check (status in ('queued', 'delivered', 'failed', 'cancelled', 'dead_letter'));
