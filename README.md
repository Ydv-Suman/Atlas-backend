# Atlas Backend

Mobile-first AI coding agent platform. Prompt AI agents from your phone to write code, review diffs, and push to GitHub — no laptop required.

## What is Atlas?

Atlas lets developers prompt AI agents (Claude, GPT-4, Gemini) to generate code, review unified diffs, run API tests, and push to GitHub entirely from iOS/Android. Built with React Native. The phone acts as a thin terminal; all computation runs server-side in isolated microservices.

**Core loop:** Prompt → AI generates diff → review on phone → approve → push to GitHub

## Architecture

```
React Native App (iOS / Android)
        |
  api-gateway :8080 — JWT validation, rate limiting, routing
        |
        +---> auth-service        :8085  (users, email verification, GitHub OAuth, credits, subscription)
        +---> workspace-service   :8090  (projects, GitHub repo listing, repo validation, repo creation)
        +---> agent-service       :9000  (RAG, LLM calls, diff generation, containers)
        +---> notification-service:8095  (FCM push, WebSocket job status)
```

### Architectural Rules

- Phone is a display terminal. Zero compute, zero secrets, zero git operations on device.
- API Gateway is the single entry point. Every request is JWT-validated before routing.
- Each service owns its own PostgreSQL database. No cross-service database access.
- Services communicate via OpenFeign HTTP clients only.
- No cross-service foreign keys. UUIDs stored as plain columns; integrity enforced at application layer.
- Onboarding gate enforced at gateway level: unverified email or missing GitHub authorization blocks all project routes.
- GitHub OAuth lives in auth-service — it is part of the authentication/onboarding flow.

## Tech Stack

| Layer          | Technology                                                  |
|----------------|-------------------------------------------------------------|
| Language       | Java 25                                                     |
| Framework      | Spring Boot 4.1, Spring Security, Spring Data JPA           |
| Build          | Maven multi-module (parent POM + shared-lib)                |
| Database       | PostgreSQL + pgvector                                       |
| Cache          | Redis                                                       |
| Auth           | Stateless JWT (HS256) + BCrypt(12)                          |
| Migrations     | Flyway                                                      |
| Containers     | Docker, Kubernetes (Tilt local), Railway (prod)             |
| Email          | Spring Mail (SMTP)                                          |
| Notifications  | Firebase Cloud Messaging (FCM), WebSocket                   |
| AI Providers   | Claude, OpenAI, Gemini                                      |

## Services

| Service              | Port | Database            | Status      | Responsibility                                                              |
|----------------------|------|---------------------|-------------|-----------------------------------------------------------------------------|
| api-gateway          | 8080 | — (stateless)       | Planned     | JWT validation, rate limiting, onboarding gate, route to downstream         |
| auth-service         | 8085 | atlas_auth_db       | Implemented | Registration, email verification, GitHub OAuth, JWT issuance, credits       |
| workspace-service    | 8090 | atlas_workspace_db  | Implemented | Project CRUD, GitHub repo listing, repo validation, repo creation           |
| agent-service        | 9000 | atlas_agent_db      | Implemented | Agent job queue, RAG pipeline, LLM routing, diff generation, git operations |
| notification-service | 8095 | — (stateless)       | Implemented | FCM push on job completion, WebSocket streaming for real-time job status     |

## Repository Structure

```
atlas-backend/
├── pom.xml                  # Parent POM — Spring Boot 4.1, Java 25, module list
├── shared-lib/              # Contracts only — DTOs, exceptions, shared JWT security
├── services/
│   ├── auth-service/        # :8085 — users, verification, GitHub OAuth, credits
│   ├── workspace-service/   # :8090 — projects, GitHub repos, repo validation
│   ├── agent-service/       # :9000 — RAG, LLM, diff, containers (Phase 2)
│   └── notification-service/# :8095 — FCM, WebSocket
├── docker-compose/
│   └── docker-compose.yml   # Local dev infrastructure
├── platform/
│   ├── k8s/                 # Kubernetes manifests (services + infra)
│   └── railway/             # Production deploy configs
├── .github/workflows/       # CI/CD
├── Tiltfile                 # Local k8s dev environment
└── Makefile                 # make up / down / build / test
```

## API Overview

All endpoints use media-type versioning: append `?v=1.0` to the URL.
WebConfig adds `/api/` prefix to all controller paths automatically.

### auth-service — `/api/auth/`, `/api/users/`, `/api/github/`

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/users/register/public?v=1.0` | Public | Register new user |
| POST | `/api/users/verify-email?v=1.0` | Public | Verify email with OTP |
| POST | `/api/users/resend-otp?v=1.0` | Public | Resend OTP |
| POST | `/api/auth/login?v=1.0` | Public | Login, returns JWT |
| POST | `/api/auth/logout?v=1.0` | Bearer | Logout (blacklists token) |
| GET | `/api/users/fetch?v=1.0` | Bearer | Get current user profile |
| PUT | `/api/users/update?v=1.0` | Bearer | Update user profile |
| DELETE | `/api/users/delete?v=1.0` | Bearer | Delete current user |
| POST | `/api/github/authorize?v=1.0` | Bearer | Returns GitHub OAuth URL |
| GET | `/api/github/callback` | Public | GitHub OAuth callback |
| GET | `/api/github/internal/token/{username}` | Internal | Decrypted GitHub token (service-to-service) |

### workspace-service — `/api/workspace/`

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/workspace/repos?v=1.0` | Bearer | List user's GitHub repos |
| POST | `/api/workspace/projects?v=1.0` | Bearer | Create project (validates push access) |
| GET | `/api/workspace/projects?v=1.0` | Bearer | List user's projects |
| GET | `/api/workspace/projects/{id}?v=1.0` | Bearer | Get project by ID |
| DELETE | `/api/workspace/projects/{id}?v=1.0` | Bearer | Delete project |

### notification-service — `/api/notify/`, `/api/ws/`

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/notify` | Internal | Send FCM push notification to user's devices |
| POST | `/api/notify/ws` | Internal | Push real-time status update to connected WebSocket client |
| WS | `/api/ws/jobs/{jobId}?token=JWT` | Bearer (query param) | WebSocket connection for live job status |

### agent-service — `/api/agent/`

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/agent/jobs` | Bearer | Submit a new agent job |
| GET | `/api/agent/jobs/{jobId}` | Bearer | Get job status and result |
| GET | `/api/agent/jobs/project/{projectId}` | Bearer | List jobs for a project |
| POST | `/api/agent/git/push/{jobId}` | Bearer | Push approved diff to branch |
| POST | `/api/agent/git/pr/{jobId}` | Bearer | Create pull request from branch |
| POST | `/api/agent/webhook/github` | Public (HMAC) | GitHub Actions workflow callback |

See each service's README for request/response bodies.

## Onboarding Flow

```
1. POST /api/users/register/public     → 201 (async OTP email sent)
2. POST /api/users/verify-email        → 200 (email_verified=true, free credits granted)
3. POST /api/auth/login                → 200 + JWT token
4. POST /api/github/authorize          → returns GitHub OAuth URL
5. User authorizes on GitHub           → callback sets github_authorized=true
6. GET  /api/workspace/repos           → list repos
7. POST /api/workspace/projects        → create project from selected repo
```

- Public repos: allowed on free tier (uses free credits)
- Private repos: allowed on free tier (uses free credits)
- Test/API/Preview: requires Pro upgrade (compute-heavy)

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
   cp services/workspace-service/.env.example services/workspace-service/.env
   ```

   Required variables per service:

   **auth-service:**
   ```properties
   DB_URL=jdbc:postgresql://<host>/<dbname>?sslmode=require
   DB_USERNAME=your_db_user
   DB_PASSWORD=your_db_password
   JWT_SECRET=your_jwt_secret
   JWT_EXPIRATION_MS=86400000
   REDIS_HOST=localhost
   REDIS_PORT=6379
   MAIL_HOST=smtp.gmail.com
   MAIL_PORT=587
   MAIL_USERNAME=your_email@gmail.com
   MAIL_PASSWORD=your_app_password
   ENCRYPTION_KEY=your_hex_encryption_key
   GITHUB_CLIENT_ID=your_github_oauth_client_id
   GITHUB_CLIENT_SECRET=your_github_oauth_client_secret
   GITHUB_REDIRECT_URI=http://localhost:8085/api/github/callback
   ```

   **workspace-service:**
   ```properties
   DB_URL=jdbc:postgresql://<host>/<dbname>?sslmode=require
   DB_USERNAME=your_db_user
   DB_PASSWORD=your_db_password
   JWT_SECRET=your_jwt_secret
   AUTH_SERVICE_URL=http://localhost:8085
   ```

   **notification-service:**
   ```properties
   JWT_SECRET=your_jwt_secret
   FIREBASE_CREDENTIALS_PATH=/absolute/path/to/firebase-service-account.json
   AUTH_SERVICE_URL=http://localhost:8085
   ```

   **agent-service:**
   ```properties
   DB_URL=jdbc:postgresql://<host>/<dbname>?sslmode=require
   DB_USERNAME=your_db_user
   DB_PASSWORD=your_db_password
   JWT_SECRET=your_jwt_secret
   GEMINI_API_KEY=your_gemini_api_key
   GITHUB_WEBHOOK_SECRET=your_webhook_secret
   AUTH_SERVICE_URL=http://localhost:8085
   WORKSPACE_SERVICE_URL=http://localhost:8090
   NOTIFICATION_SERVICE_URL=http://localhost:8095
   ```

   `JWT_SECRET` must be identical across all services.

3. **Build all modules from root**
   ```bash
   mvn compile
   ```
   Builds in order: shared-lib → auth-service → workspace-service → api-gateway → notification-service → agent-service.

4. **Start infrastructure with Docker Compose**
   ```bash
   docker-compose -f docker-compose/docker-compose.yml up -d
   ```

5. **Run services locally** (requires infrastructure from step 4)
   ```bash
   # Terminal 1 — auth-service
   mvn -f services/auth-service/pom.xml spring-boot:run

   # Terminal 2 — workspace-service
   mvn -f services/workspace-service/pom.xml spring-boot:run

   # Terminal 3 — notification-service
   mvn -f services/notification-service/pom.xml spring-boot:run

   # Terminal 4 — agent-service
   mvn -f services/agent-service/pom.xml spring-boot:run
   ```

## Project Phases

| Phase | Name                              | Status      |
|-------|-----------------------------------|-------------|
| 1     | Monorepo + Microservice Foundation| Complete    |
| 2     | Agent Pipeline                    | Complete    |
| 3     | React Native App                  | Planned     |
| 4     | Testing Lab + Preview             | Planned     |
| 5     | Pro Tier + Warm Containers        | Planned     |

## Related Repositories

- **atlas-mobile** — React Native app (iOS + Android). Separate repo; communicates via REST API only.

## License

Confidential. Internal use only.
