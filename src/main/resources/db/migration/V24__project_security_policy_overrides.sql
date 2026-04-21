create table project_security_policies (
    project_id uuid primary key references projects(id) on delete cascade,
    attachment_max_upload_bytes bigint,
    attachment_max_download_bytes bigint,
    attachment_allowed_content_types text,
    export_max_artifact_bytes bigint,
    export_allowed_content_types text,
    import_max_parse_bytes bigint,
    import_allowed_content_types text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_project_security_policy_attachment_upload check (attachment_max_upload_bytes is null or attachment_max_upload_bytes > 0),
    constraint ck_project_security_policy_attachment_download check (attachment_max_download_bytes is null or attachment_max_download_bytes > 0),
    constraint ck_project_security_policy_export_artifact check (export_max_artifact_bytes is null or export_max_artifact_bytes > 0),
    constraint ck_project_security_policy_import_parse check (import_max_parse_bytes is null or import_max_parse_bytes > 0)
);

create trigger trg_project_security_policies_updated_at
    before update on project_security_policies
    for each row execute function set_updated_at();
