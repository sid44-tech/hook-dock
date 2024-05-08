-- Existing projects are assigned to the earliest user on startup (see AdminBootstrap).
alter table project add column owner_id uuid references app_user (id);

create index idx_project_owner on project (owner_id);
