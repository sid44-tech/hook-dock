# Contributing

Thanks for taking a look. Issues and pull requests are welcome.

## Getting set up

You need JDK 21. Docker is only needed if you want to run against PostgreSQL locally.

```bash
./mvnw verify
```

The test suite runs against an in-memory H2 database in PostgreSQL mode, so it doesn't need Docker.

## Guidelines

- Keep changes focused. One feature or fix per pull request.
- Add or update tests for behaviour changes. Anything touching outbound delivery
  (`destinations/`, `delivery/`) needs tests for the failure cases too.
- Schema changes go in a new Flyway migration under `src/main/resources/db/migration`.
  Never edit a migration that has already been released.
- Never commit secrets, real webhook payloads or `.env` files.
- Check `ROADMAP.md` before starting something big, and open an issue first so we can agree on the approach.
