# Roadmap

What's left after the initial capture / forward / replay work. Estimates are rough.

## Delivery

- Automatic retries with exponential backoff and jitter. Configurable retryable statuses (429, 5xx), max attempts, and a terminal dead-letter state. `TriggerType.AUTOMATIC_RETRY` exists but nothing creates it yet. (~5h)
- Recover attempts stuck in `IN_PROGRESS` after a crash. Lease columns are written but nothing checks `lease_expires_at`. (~2h)
- Close the DNS rebinding window: connect to the IP that passed the policy instead of letting the HTTP client resolve again. Document an egress proxy setup. (~4h)
- Per-destination custom headers and auth secrets, encrypted at rest and never shown in event views. (~4h)
- Concurrency limits and per-destination rate limiting in the worker. (~2h)
- Optional HTTPS-only mode for destinations in production. (~1h)

## Capture

- Retention cleanup job. `retention_days` is stored per endpoint but nothing deletes old events yet. Add a UI to edit it. (~3h)
- Per-endpoint inbound rate limiting. (~2h)
- Endpoint token rotation (new URL, old one disabled). (~1.5h)
- Endpoint delete, with confirmation. (~1h)

## UI

- General UI refresh: layout, consistent styling, better empty states, mobile, dark mode. The current pages are deliberately plain. (~6h)
- Search and filters on the event list: provider, method, delivery status, date range, text in body. (~4h)
- Copy-as-cURL for an event, with redacted headers left out. (~1.5h)
- Hex view and raw download for binary bodies. (~1.5h)
- Endpoint setup page with instructions for GitHub, Stripe and others. (~2h)
- Side-by-side comparison of responses across attempts. (~3h)
- Live updates on the event list using server-sent events. (~3h)

## Replay

- Replay with a modified payload, stored as a separate, clearly labelled immutable variant. (~5h)
- Optional test signing with a user-provided secret for providers like GitHub and Stripe. (~4h)

## Users and access

- Project members with roles (owner, member, viewer) instead of single-owner projects. (~6h)
- User management: create users, change password, disable accounts. Today only the bootstrap admin exists. (~3h)
- Audit log page for replays, destination changes and endpoint changes. (~2h)

## Testing and operations

- PostgreSQL integration tests with Testcontainers alongside the H2 suite. (~3h)
- Metrics via Micrometer: captures, delivery latency, failures by category. (~2h)
- Release workflow: build and publish a container image, versioned tags. (~2h)
- Example receiver app and a scripted "fail, fix, replay" demo. (~3h)

## Later

- Provider-specific signature verification plugins.
- Export sanitized events as test fixtures.
- Spring Boot starter for local webhook development.
