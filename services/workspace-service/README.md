# Workspace Service

Spring Boot service for workspace and project management in Atlas. Handles GitHub repo listing, project CRUD, and repo creation.

## Requirements

- Java 25
- Maven
- PostgreSQL
- Auth-service running (for GitHub token retrieval via Feign)

## Configuration

Copy `.env.example` to `.env` in this directory and fill in the values:

```properties
DB_URL=jdbc:postgresql://<host>/<dbname>?sslmode=require
DB_USERNAME=your_db_user
DB_PASSWORD=your_db_password
JWT_SECRET=your_jwt_secret
AUTH_SERVICE_URL=http://localhost:8085
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

atlas:
  auth-service:
    url: ${AUTH_SERVICE_URL}
```

If you run the service from the repo root, Spring can read `services/workspace-service/.env`.
If you run it from inside `services/workspace-service`, it can read the local `.env`.

## Security

- Stateless JWT authentication via shared-lib `JwtAuthenticationFilter`
- CSRF disabled (pure API service, no browser cookies)
- All `/api/workspace/**` endpoints require Bearer token
- `/actuator/**` is public

### Security Headers

- `X-Frame-Options: DENY`
- `Strict-Transport-Security: max-age=31536000; includeSubDomains`
- `X-Content-Type-Options: nosniff`
- `Content-Security-Policy: default-src 'self'`
- `Permissions-Policy: geolocation=(), microphone=(), camera=()`
- `Cache-Control: no-cache, no-store`
- `Referrer-Policy: no-referrer`

## API Endpoints

All endpoints use media-type versioning: append `?v=1.0` to the URL.
All endpoints require `Authorization: Bearer <token>` header.

### GitHub Repos

| Method | Path | Description |
|---|---|---|
| GET | `/api/workspace/repos?v=1.0` | List authenticated user's GitHub repositories |

Response:

```json
[
    {
        "id": 123456789,
        "name": "my-repo",
        "full_name": "username/my-repo",
        "description": "My project",
        "html_url": "https://github.com/username/my-repo",
        "clone_url": "https://github.com/username/my-repo.git",
        "language": "Java",
        "default_branch": "main",
        "private": false,
        "fork": false,
        "stargazers_count": 5,
        "updated_at": "2026-07-14T10:00:00Z",
        "owner": {
            "login": "username",
            "avatar_url": "https://avatars.githubusercontent.com/u/..."
        }
    }
]
```

### Project CRUD

| Method | Path | Description | Status |
|---|---|---|---|
| POST | `/api/workspace/projects?v=1.0` | Create project from GitHub repo | 201 |
| GET | `/api/workspace/projects?v=1.0` | List user's projects | 200 |
| GET | `/api/workspace/projects/{id}?v=1.0` | Get project by ID (ownership verified) | 200 |
| DELETE | `/api/workspace/projects/{id}?v=1.0` | Delete project (ownership verified) | 204 |

#### Create Project — `POST /api/workspace/projects?v=1.0`

Request:

```json
{
    "projectName": "Atlas Backend",
    "framework": "spring_boot",
    "githubUrl": "https://github.com/username/my-repo",
    "repoOwner": "username",
    "repoOwnership": "OWNER",
    "repoVisibility": "PUBLIC",
    "projectType": "backend",
    "createIfNotExists": false
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `projectName` | String | Yes | Display name (1–255 chars) |
| `framework` | String | No | `spring_boot`, `fastapi`, `express`, `nestjs`, `django` |
| `githubUrl` | String | Yes | Full GitHub URL (1–500 chars) |
| `repoOwner` | String | Yes | GitHub username or org that owns the repo |
| `repoOwnership` | String | Yes | `OWNER`, `COLLABORATOR`, or `FORK` |
| `repoVisibility` | String | Yes | `PUBLIC` or `PRIVATE` |
| `projectType` | String | No | `backend`, `frontend`, `fullstack` |
| `createIfNotExists` | Boolean | No | Default `false`. If `true` and repo doesn't exist, creates it on GitHub |

Response (201):

```json
{
    "id": 1,
    "projectName": "Atlas Backend",
    "framework": "spring_boot",
    "githubUrl": "https://github.com/username/my-repo",
    "repoOwner": "username",
    "repoOwnership": "OWNER",
    "repoVisibility": "PUBLIC",
    "projectType": "backend",
    "createdAt": "2026-07-14T15:50:05.230955Z",
    "lastSyncedAt": "2026-07-14T15:50:05.230955Z"
}
```

#### Validation Rules

- User must have **push access** to the repo (verified via GitHub API)
- Duplicate workspaces for same repo URL are rejected (409)
- If repo doesn't exist and `createIfNotExists` is `false`, returns error with suggestion
- If repo doesn't exist and `createIfNotExists` is `true`, creates repo on GitHub with `auto_init: true`

#### Error Responses

| Status | Condition |
|---|---|
| 400 | Validation failure, repo not found (without `createIfNotExists`), no push access |
| 404 | Workspace not found or not owned by user |
| 409 | Workspace already exists for this repo |

Error shape:

```json
{
    "apiPath": "/api/workspace/projects",
    "errorCode": "400 BAD_REQUEST",
    "errorMessage": "You don't have push access to this repository",
    "errorTime": "2026-07-14T16:00:00.000000"
}
```

## Inter-Service Communication

Workspace-service calls auth-service via OpenFeign to retrieve GitHub tokens:

```
GET {AUTH_SERVICE_URL}/api/github/internal/token/{username}
```

This is a service-to-service call — no JWT required. Auth-service whitelists this path.

## Database Schema

Flyway manages schema migrations in `src/main/resources/db/migration/`:

- `V1__workspace_table.sql` — `workspace` table

| Column | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL | PK, auto-increment |
| `user_id` | VARCHAR(255) | NOT NULL, indexed |
| `project_name` | VARCHAR(150) | NOT NULL |
| `framework` | VARCHAR(255) | nullable |
| `github_url` | VARCHAR(500) | NOT NULL |
| `repo_owner` | VARCHAR(150) | NOT NULL |
| `repo_ownership` | VARCHAR(50) | OWNER, COLLABORATOR, FORK |
| `repo_visibility` | VARCHAR(50) | PUBLIC, PRIVATE |
| `project_type` | VARCHAR(255) | nullable |
| `created_at` | TIMESTAMPTZ | DEFAULT now() |
| `last_synched_at` | TIMESTAMPTZ | DEFAULT now() |

## Run the Service

From this directory:

```bash
./mvnw spring-boot:run
```

Or from the repo root:

```bash
mvn -f services/workspace-service/pom.xml spring-boot:run
```

## Workflow

### Create Project Flow

```
1. GET  /api/workspace/repos?v=1.0              → list GitHub repos
2. User selects a repo from the list
3. POST /api/workspace/projects?v=1.0            → create project (validates push access)
   - If repo missing → error: "Set createIfNotExists to true"
   - Resend with createIfNotExists: true          → creates repo on GitHub + workspace
4. GET  /api/workspace/projects?v=1.0            → list all projects
5. GET  /api/workspace/projects/{id}?v=1.0       → get project detail
6. DELETE /api/workspace/projects/{id}?v=1.0     → delete project
```
