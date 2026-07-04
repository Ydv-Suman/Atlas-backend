# Atlas Backend

Mobile-first AI coding agent platform. Prompt AI agents from your phone to write code, review diffs, and push to GitHub — no laptop required.

## What is Atlas?

Atlas lets developers prompt AI agents (Claude, GPT-4, Gemini) to generate code, review unified diffs, run API tests, and push to GitHub entirely from iOS/Android. The phone acts as a thin terminal; all computation runs server-side in isolated microservices.

**Core loop:** Prompt → AI generates diff → review on phone → approve → push to GitHub

## Architecture

```
Flutter App (iOS / Android)
        |
  api-gateway :8080 — JWT validation, rate limiting, routing
        |
        +---> auth-service        :8081  (users, email verification, credits, subscription)
        +---> project-service     :8082  (projects, workspaces, repo visibility check)
        +---> agent-service       :8083  (RAG, LLM calls, diff generation, containers)
        +---> github-service      :8084  (OAuth, clone, push, PR creation)
        +---> notification-service:8085  (FCM push, WebSocket job status)
```

### Architectural Rules

- Phone is a display terminal. Zero compute, zero secrets, zero git operations on device.
- API Gateway is the single entry point. Every request is JWT-validated before routing.
- Each service owns its own PostgreSQL database. No cross-service database access.
- Services communicate via OpenFeign HTTP clients only.
- No cross-service foreign keys. UUIDs stored as plain columns; integrity enforced at application layer.
- Onboarding gate enforced at gateway level: unverified email or missing GitHub authorization blocks all project routes.

## Tech Stack

| Layer          | Technology                                                  |
|----------------|-------------------------------------------------------------|
| Language       | Java 25                                                     |
| Framework      | Spring Boot 4.1, Spring Security, Spring Data JPA           |
| Build          | Maven multi-module (parent POM + shared-lib)                |
| Database       | PostgreSQL + pgvector                                       |
| Cache          | Redis                                                       |
| Auth           | Stateless JWT + CSRF cookie protection, BCrypt(12)          |
| Migrations     | Flyway                                                      |
| Containers     | Docker, Kubernetes (Tilt local), Railway (prod)             |
| Email          | Spring Mail (SMTP)                                          |
| Notifications  | Firebase Cloud Messaging (FCM), WebSocket                   |
| AI Providers   | Claude, OpenAI, Gemini                                      |

## Services

| Service              | Port | Database       | Responsibility                                                              |
|----------------------|------|----------------|-----------------------------------------------------------------------------|
| api-gateway          | 8080 | — (stateless)  | JWT validation, rate limiting, onboarding gate, route to downstream         |
| auth-service         | 8081 | atlas_auth_db  | Registration, email verification, JWT issuance, credits, subscriptions      |
| project-service      | 8082 | atlas_proj_db  | Project CRUD, workspace metadata, repo visibility, Pro-gate enforcement     |
| agent-service        | 8083 | atlas_agent_db | Agent job queue, RAG pipeline, LLM routing, diff generation, test runner    |
| github-service       | 8084 | atlas_gh_db    | GitHub OAuth, token encryption, repo clone, branch push, PR creation        |
| notification-service | 8085 | — (stateless)  | FCM push on job completion, WebSocket streaming for real-time job status    |

## Repository Structure

```
atlas-backend/
├── pom.xml                  # Parent POM — Spring Boot 3, Java 21, module list
├── shared-lib/              # Contracts only — DTOs, events, shared security, exceptions
├── services/
│   ├── api-gateway/         # :8080 — entry point
│   ├── auth-service/        # :8081 — users, verification, credits, subscription
│   ├── project-service/     # :8082 — projects, workspaces, repo visibility
│   ├── agent-service/       # :8083 — RAG, LLM, diff, containers
│   ├── github-service/      # :8084 — OAuth, clone, push, PR
│   └── notification-service/# :8085 — FCM, WebSocket
├── platform/
│   ├── k8s/                 # Kubernetes manifests (services + infra)
│   └── railway/             # Production deploy configs
├── .github/workflows/       # CI/CD
├── Tiltfile                 # Local k8s dev environment
└── Makefile                 # make up / down / build / test
```

## Onboarding Flow

```
Register → Email OTP sent → Verify email → Free credits granted
→ GitHub OAuth authorization → Create projects and run agent jobs
```

- Public repos: allowed on free tier (uses free credits)
- Private repos: requires Pro upgrade

## Prerequisites

- Java 25
- Maven 3.9+
- Docker
- PostgreSQL 16+
- Redis 7+

## Getting Started

1. **Clone the repo**
   ```bash
   git clone https://github.com/your-org/atlas-backend.git
   cd atlas-backend
   ```

2. **Set up environment variables**

   Each service has its own `.env` file:
   ```bash
   cp services/auth-service/.env.example services/auth-service/.env
   cp services/github-service/.env.example services/github-service/.env
   ```
   Required variables per service:
   - **All services**: `JWT_SECRET` (must be identical across services)
   - **auth-service**: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `JWT_EXPIRATION_MS`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`
   - **github-service**: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`, `ENCRYPTION_KEY`, `AUTH_SERVICE_URL`

3. **Build all modules from root**
   ```bash
   mvn compile
   ```
   Builds in order: shared-lib → auth-service → github-service.

4. **Start infrastructure with Docker Compose**
   ```bash
   docker-compose -f docker-compose/docker-compose.yml up -d
   ```

5. **Run a single service locally** (requires infrastructure from step 4)
   ```bash
   cd services/auth-service
   mvn spring-boot:run
   ```

## Project Phases

| Phase | Name                              | Status      |
|-------|-----------------------------------|-------------|
| 1     | Monorepo + Microservice Foundation| In Progress |
| 2     | Agent Pipeline                    | Planned     |
| 3     | Flutter App                       | Planned     |
| 4     | Testing Lab + Preview             | Planned     |
| 5     | Pro Tier + Warm Containers        | Planned     |

## Related Repositories

- **atlas-mobile** — Flutter app (iOS + Android). Separate repo; communicates via REST API only.

## License

Confidential. Internal use only.
