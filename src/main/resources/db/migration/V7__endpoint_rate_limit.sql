-- Per-endpoint override of hookdock.rate-limit.requests. Null means the
-- endpoint uses the server default. The upper bound is enforced in the app.
alter table webhook_endpoint add column rate_limit_requests integer;

alter table webhook_endpoint add constraint chk_webhook_endpoint_rate_limit
    check (rate_limit_requests is null or rate_limit_requests > 0);
