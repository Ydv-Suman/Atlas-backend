# Workspace Service

Spring Boot service for workspace and project management in Atlas.

## Requirements

- Java 25
- Maven
- PostgreSQL

## Configuration

Copy `.env.example` to `.env` in this directory and fill in the values:

```properties
DB_URL=jdbc:postgresql://<host>/<dbname>?sslmode=require
DB_USERNAME=your_db_user
DB_PASSWORD=your_db_password
JWT_SECRET=your_jwt_secret
```

`application.yaml` reads these values from environment variables or from `.env`:

```yaml
spring:
  config:
    import: optional:file:.env[.properties],optional:file:services/workspace-service/.env[.properties]
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
```

If you run the service from the repo root, Spring can read `services/workspace-service/.env`.
If you run it from inside `services/workspace-service`, it can read the local `.env`.

## Security

- Stateless JWT authentication via shared-lib `JwtAuthenticationFilter`
- CSRF disabled (pure API service, no browser cookies)
- All `/api/workspaces/**` endpoints require Bearer token
- `/actuator/health` is public

### Security Headers

- `X-Frame-Options: DENY`
- `Strict-Transport-Security: max-age=31536000; includeSubDomains`
- `X-Content-Type-Options: nosniff`
- `Content-Security-Policy: default-src 'self'`
- `Permissions-Policy: geolocation=(), microphone=(), camera=()`
- `Cache-Control: no-cache, no-store`
- `Referrer-Policy: no-referrer`

## API Endpoints

### Authenticated (Bearer token required)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/workspaces` | Create workspace |
| GET | `/api/v1/workspaces` | List user workspaces |
| GET | `/api/v1/workspaces/{id}` | Get workspace by ID |
| PUT | `/api/v1/workspaces/{id}` | Update workspace |
| DELETE | `/api/v1/workspaces/{id}` | Delete workspace |

## Database Schema

Flyway manages schema migrations in `src/main/resources/db/migration/`:

- `V1__workspace_table.sql` — `workspaces` table: `id`, `user_id`, `name`, `description`, `github_url`, `repo_owner`, `repo_name`, `default_branch`, `repo_visibility`, `ownership`, `framework`, `project_type`, `last_synced_at`, `created_at`, `updated_at`

### Workspace Ownership Types

| Type | Description |
|------|-------------|
| `OWNER` | User owns the repo |
| `COLLABORATOR` | User has access to someone else's repo |
| `FORK` | User forked the repo |

## Run Flyway

Flyway runs automatically on application startup. To run manually:

```bash
DB_URL="jdbc:postgresql://<host>/<dbname>?sslmode=require" \
DB_USERNAME="your_user" \
DB_PASSWORD="your_password" \
./mvnw flyway:migrate
```

## Run the Service

From this directory:

```bash
./mvnw spring-boot:run
```

Or from the repo root:

```bash
mvn -f services/workspace-service/pom.xml spring-boot:run
```
