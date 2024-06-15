alter table webhook_endpoint add column forward_destination_id uuid references destination (id) on delete set null;

create table delivery_attempt (
    id                    uuid primary key,
    captured_webhook_id   uuid not null references captured_webhook (id) on delete cascade,
    destination_id        uuid not null references destination (id) on delete cascade,
    attempt_number        integer not null,
    trigger_type          varchar(20) not null,
    status                varchar(20) not null,
    queued_at             timestamp with time zone not null,
    started_at            timestamp with time zone,
    completed_at          timestamp with time zone,
    duration_ms           bigint,
    http_status           integer,
    response_headers      text,
    response_body_excerpt text,
    error_category        varchar(30),
    error_message         varchar(1000),
    requested_by_user_id  uuid references app_user (id) on delete set null,
    lease_owner           varchar(100),
    lease_expires_at      timestamp with time zone,
    unique (captured_webhook_id, attempt_number)
);

create index idx_delivery_attempt_event on delivery_attempt (captured_webhook_id);
create index idx_delivery_attempt_pending on delivery_attempt (status, queued_at);
