# Atlas Backend

Monorepo for Atlas backend services.

## Services

- [`auth-service`](services/auth-service/README.md)

## Shared Setup

- PostgreSQL is used for local development.
- Each service owns its own configuration and migrations.
- Secrets should come from environment variables or a local `.env` file that is not committed.

## Local PostgreSQL

See the auth service README for the Docker and `psql` commands:

- [`services/auth-service/README.md`](services/auth-service/README.md)
