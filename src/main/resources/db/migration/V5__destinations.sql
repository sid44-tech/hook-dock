create table destination (
    id         uuid primary key,
    project_id uuid not null references project (id) on delete cascade,
    name       varchar(100) not null,
    url        varchar(2048) not null,
    enabled    boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index idx_destination_project on destination (project_id);
