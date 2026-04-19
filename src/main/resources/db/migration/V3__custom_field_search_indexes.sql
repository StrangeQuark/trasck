create index if not exists ix_custom_field_values_field_scalar
    on custom_field_values(custom_field_id, (value #>> '{}'));
