create table app_user (
    id            uuid primary key,
    username      varchar(100) not null unique,
    password_hash varchar(255) not null,
    created_at    timestamp with time zone not null
);
