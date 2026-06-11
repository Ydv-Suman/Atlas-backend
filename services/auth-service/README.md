# Auth Service

Spring Boot service for authentication and user management.

## Requirements

- Java 25
- Maven
- PostgreSQL
- Docker, if you want to run PostgreSQL locally in a container

## Configuration

Copy `.env.example` to `.env` in this directory and fill in the values:

```properties
DB_URL=jdbc:postgresql://localhost:5432/auth
DB_USERNAME=username
DB_PASSWORD=change-me
```

`application.yaml` reads these values from environment variables or from `.env`:

```yaml
spring:
  config:
    import: optional:file:.env[.properties],optional:file:services/auth-service/.env[.properties]
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
```

If you run the service from the repo root, Spring can read `services/auth-service/.env`.
If you run it from inside `services/auth-service`, it can read the local `.env`.

## Run PostgreSQL in Docker

Start PostgreSQL with a bind-mounted data directory:

```bash
docker run -d \
  --name atlasAuthDB \
  -e POSTGRES_DB=auth \
  -e POSTGRES_USER=suman \
  -e POSTGRES_PASSWORD=<password> \
  -p 5432:5432 \
  -v /Volumes/Storage/Data/postgres:/var/lib/postgresql/data \
  postgres:16
```

If you already have data in that folder, PostgreSQL will reuse it.

## Access PostgreSQL

Open `psql` inside the container:

```bash
docker exec -it atlasAuthDB psql -U username -d auth
```

Connect from your machine:

```bash
psql -h localhost -p 5432 -U username -d auth -W
```

Useful `psql` commands:

```sql
\l
\dt
\d users
SELECT * FROM users;
```

## Database Schema

Flyway creates the `users` table in:

- [`src/main/resources/db/migration/V1__user_schema.sql`](src/main/resources/db/migration/V1__user_schema.sql)

That table includes:

- `created_at`
- `updated_at`
- `email_verified`
- `github_authorized`
- `tier` constrained to `FREE` or `PRO`

## Run Flyway

Set the database env vars in your shell first:

```bash
export DB_URL=jdbc:postgresql://localhost:5432/auth
export DB_USERNAME=username
export DB_PASSWORD=your_password
```

Then run Flyway from this module:

```bash
mvn -f services/auth-service/pom.xml flyway:info
mvn -f services/auth-service/pom.xml flyway:validate
mvn -f services/auth-service/pom.xml flyway:migrate
```

Useful commands:
- `flyway:info` shows applied and pending migrations
- `flyway:validate` checks migration consistency
- `flyway:migrate` applies pending migrations

## Run the Service

From this directory:

```bash
./mvnw spring-boot:run
```

Or from the repo root:

```bash
mvn -f services/auth-service/pom.xml spring-boot:run
```

## Notes

- `createdAt` and `updatedAt` are handled in the backend by the entity lifecycle methods.
- The service does not need database triggers for audit timestamps.
