create table captured_webhook (
    id                uuid primary key,
    endpoint_id       uuid not null references webhook_endpoint (id) on delete cascade,
    received_at       timestamp with time zone not null,
    method            varchar(10) not null,
    raw_path          varchar(2048) not null,
    query_string      varchar(4096),
    request_headers   text not null,
    request_body      bytea,
    content_type      varchar(255),
    body_size         integer not null,
    provider          varchar(50),
    provider_event_id varchar(255),
    capture_status    varchar(20) not null
);

create index idx_captured_webhook_endpoint_received on captured_webhook (endpoint_id, received_at desc);
