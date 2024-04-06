create table project (
    id          uuid primary key,
    name        varchar(100) not null,
    description varchar(500),
    created_at  timestamp with time zone not null
);

create table webhook_endpoint (
    id                 uuid primary key,
    project_id         uuid not null references project (id) on delete cascade,
    name               varchar(100) not null,
    token_hash         varchar(64) not null unique,
    enabled            boolean not null default true,
    forwarding_enabled boolean not null default false,
    retention_days     integer not null default 30,
    created_at         timestamp with time zone not null
);

create index idx_webhook_endpoint_project on webhook_endpoint (project_id);
