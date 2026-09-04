# Agent Service

Spring Boot service for AI-powered code generation. Accepts user prompts, retrieves code context via RAG, calls LLM providers (Claude, OpenAI, Gemini), and returns validated diffs.

## Requirements

- Java 25
- Maven
- Neon PostgreSQL (cloud-hosted) with pgvector extension
- Git (required at runtime for diff operations)

## Configuration

Copy `.env.example` to `.env` in this directory and fill in the values:

```properties
DB_URL=jdbc:postgresql://<neon-host>/<dbname>?sslmode=require
DB_USERNAME=neondb_owner
DB_PASSWORD=your_neon_password
JWT_SECRET=your_jwt_secret
GEMINI_API_KEY=your_gemini_api_key
GITHUB_WEBHOOK_SECRET=your_webhook_secret
AUTH_SERVICE_URL=http://localhost:8085
WORKSPACE_SERVICE_URL=http://localhost:8090
NOTIFICATION_SERVICE_URL=http://localhost:8095
```

`application.yaml` reads these values from environment variables or from `.env`:

```yaml
spring:
  config:
    import: optional:file:.env[.properties],optional:file:services/agent-service/.env[.properties]
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
```

If you run the service from the repo root, Spring can read `services/agent-service/.env`.
If you run it from inside `services/agent-service`, it can read the local `.env`.

## Infrastructure

### PostgreSQL (Neon)

Database is hosted on [Neon](https://neon.tech). No local PostgreSQL container needed.

Connection string goes in `.env` as `DB_URL` with `?sslmode=require`.

Requires the `pgvector` extension (enabled automatically by migration V2).

### Gemini API Key (Embeddings)

Used server-side for RAG embeddings (free tier). Not user-facing.

1. Go to [Google AI Studio](https://aistudio.google.com)
2. Click **Get API Key** → **Create API key**
3. Copy key into `.env` as `GEMINI_API_KEY`

No billing or credit card required. Free up to 1500 requests/minute.

### GitHub Webhook Secret

Shared secret for verifying GitHub webhook signatures:

```bash
openssl rand -hex 32
```

Set the same value in `.env` and in each connected repo's webhook settings.

## Architecture

### Pipeline Flow

```
User prompt → AgentJob (PENDING)
  → Credit check (auth-service)
  → Workspace clone/sync (WorkspaceCacheService)
  → RAG index + retrieval (Gemini embeddings + pgvector)
  → LLM call (user's API key → Claude/OpenAI/Gemini)
  → Diff validation (git apply --check)
  → Job COMPLETED → push notification + WebSocket update

On approve:
  → Push branch (JGit)
  → Create pull request (GitHub API)
```

### LLM Providers

Users provide their own API key for code generation. Supported providers:

| Provider | Model | API |
|---|---|---|
| `claude` | claude-sonnet-4-20250514 | Anthropic Messages API |
| `openai` | gpt-4o | OpenAI Chat Completions |
| `gemini` | gemini-2.5-flash | Google Generative AI |

All providers share the same contract: diff-only system prompt, temperature 0.2, one retry on malformed output.

### RAG Pipeline

- **Chunking**: 500-token chunks with 3-line overlap, file path prefix
- **Embedding**: Gemini `text-embedding-004` (768 dims, free tier, batch support)
- **Storage**: pgvector with cosine similarity (`<=>` operator)
- **Retrieval**: Top-10 most similar chunks for user prompt
- **Invalidation**: Changed files re-indexed, old chunks deleted

### Workspace Cache

- Shallow clone (depth-1) per project
- 30-minute warm window with `git fetch` + `reset --hard`
- Per-project reentrant lock (60s timeout)
- Scheduled eviction every 10 minutes

## API Endpoints

### Job Endpoints

| Method | Path | Description |
|---|---|---|
| POST | `/api/agent/jobs` | Submit a new agent job |
| GET | `/api/agent/jobs/{jobId}` | Get job status and result |
| GET | `/api/agent/jobs/project/{projectId}` | List jobs for a project |

### Git Endpoints

| Method | Path | Description |
|---|---|---|
| POST | `/api/agent/git/push/{jobId}` | Push approved diff to branch |
| POST | `/api/agent/git/pr/{jobId}` | Create pull request from branch |

### Webhook Endpoint

| Method | Path | Description |
|---|---|---|
| POST | `/api/agent/webhook/github` | GitHub Actions workflow callback |

### Request / Response Bodies

#### Submit Job — `POST /api/agent/jobs`

Headers: `X-User-Id: <uuid>`

```json
{
    "projectId": "uuid",
    "prompt": "add pagination to the /users endpoint",
    "provider": "claude"
}
```

Response (202):

```json
{
    "status": "202",
    "message": "Job submitted",
    "data": {
        "id": "uuid",
        "projectId": "uuid",
        "status": "PENDING",
        "diffOutput": null,
        "errorMessage": null,
        "creditsConsumed": null,
        "createdAt": "2026-08-08T12:00:00Z",
        "completedAt": null
    }
}
```

#### Push Diff — `POST /api/agent/git/push/{jobId}`

```json
{
    "branchName": "atlas/add-pagination",
    "commitMessage": "feat: add pagination to /users endpoint"
}
```

#### Create PR — `POST /api/agent/git/pr/{jobId}`

```json
{
    "branchName": "atlas/add-pagination",
    "title": "Add pagination to /users endpoint",
    "description": "Generated by Atlas AI agent"
}
```

## Database Schema

Flyway manages schema migrations in `src/main/resources/db/migration/`:

- `V1__agent_jobs.sql` — `agent_jobs` table: `id`, `project_id`, `user_id`, `prompt`, `agent_provider`, `status`, `diff_output`, `credits_consumed`, `error_message`, `created_at`, `completed_at`
- `V2__repo_embeddings.sql` — `repo_embeddings` table with `vector(768)` column, enables pgvector extension
- `V3__api_catalog.sql` — `api_catalog` table: `id`, `project_id`, `endpoints` (JSONB), `commit_hash`, `created_at`
- `V4__user_agent_keys.sql` — `user_agent_keys` table: `id`, `user_id`, `provider`, `encrypted_key`, `key_hint`, `created_at`

## Run the Service

From this directory:

```bash
./mvnw spring-boot:run
```

Or from the repo root:

```bash
mvn -f services/agent-service/pom.xml spring-boot:run
```

## Docker

Build from repo root:

```bash
docker build -f services/agent-service/Dockerfile -t atlas-agent-service .
```

Run:

```bash
docker run -p 9000:9000 \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/atlas_agent \
  -e DB_USERNAME=postgres \
  -e DB_PASSWORD=yourpass \
  -e JWT_SECRET=yourjwt \
  -e GEMINI_API_KEY=yourkey \
  -e GITHUB_WEBHOOK_SECRET=yoursecret \
  atlas-agent-service
```

## WebSocket Status Updates

During job execution, WebSocket updates are sent at each pipeline step:

```
RUNNING → INDEXING → RETRIEVING_CONTEXT → GENERATING_DIFF → VALIDATING → COMPLETED/FAILED
```

## Notes

- LLM API keys are user-provided and stored encrypted in `user_agent_keys`.
- Gemini embedding API key is platform-owned (free tier, no user cost).
- Async job execution uses a 3-thread pool (`agentTaskExecutor`).
- Diff validation uses `git apply --check` before marking job complete.
- Credits are consumed via auth-service after successful diff generation. 402 returned when balance empty.
- GitHub webhook signatures verified with HMAC-SHA256.
