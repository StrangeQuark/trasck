alter table views
    add column project_id uuid references projects(id) on delete cascade,
    add column team_id uuid references teams(id) on delete set null;

alter table views
    drop constraint ck_views_visibility;

alter table views
    add constraint ck_views_visibility check (visibility in ('private', 'team', 'project', 'workspace', 'public')),
    add constraint ck_views_team_visibility check (
        (visibility = 'team' and team_id is not null)
        or (visibility <> 'team' and team_id is null)
    ),
    add constraint ck_views_project_visibility check (
        (visibility = 'project' and project_id is not null)
        or (visibility <> 'project' and project_id is null)
    );

create index if not exists ix_views_workspace_visibility_project on views(workspace_id, visibility, project_id);
create index if not exists ix_views_workspace_visibility_team on views(workspace_id, visibility, team_id);
