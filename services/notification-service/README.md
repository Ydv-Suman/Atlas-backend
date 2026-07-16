# Notification Service

Spring Boot service for push notifications (Firebase Cloud Messaging) and real-time WebSocket updates.

## What This Service Does

The notification service is the central delivery layer for all user-facing notifications in Atlas. It handles two channels:

- **Push Notifications (FCM)** — When a background job completes (build, deploy, CI pipeline, etc.), other services call this service to send a push notification to the user's mobile devices. It looks up the user's registered device tokens from auth-service via Feign, then dispatches notifications through Firebase Cloud Messaging.

- **WebSocket Real-Time Updates** — For live status tracking, clients open a WebSocket connection scoped to a specific job ID. As the job progresses, other services POST status updates to this service, which forwards them instantly to the connected client. This powers live progress bars, step-by-step build logs, and real-time status indicators in the frontend.

### Flow

```
Other Atlas services (workspace-service, CI runner, etc.)
        │
        ├── POST /api/notify       → FCM push to user's phone/tablet
        │
        └── POST /api/notify/ws    → Real-time update to connected browser/app
                                      via WebSocket at /api/ws/jobs/{jobId}
```

This service does not persist notifications — it is a stateless pass-through delivery layer. Job state and history live in the originating services.

## Requirements

- Java 25
- Maven
- Firebase project with Cloud Messaging enabled
- Auth service running (for device token lookup)

## Configuration

Copy `.env.example` to `.env` in this directory and fill in the values:

```properties
JWT_SECRET=your_jwt_secret
FIREBASE_CREDENTIALS_PATH=/absolute/path/to/firebase-service-account.json
AUTH_SERVICE_URL=http://localhost:8085
```

`application.yaml` reads these values from environment variables or from `.env`:

```yaml
spring:
  config:
    import: optional:file:.env[.properties],optional:file:services/notification-service/.env[.properties]
```

If you run the service from the repo root, Spring can read `services/notification-service/.env`.
If you run it from inside `services/notification-service`, it can read the local `.env`.

### Firebase Setup

1. Go to [Firebase Console](https://console.firebase.google.com)
2. Create or select a project
3. Enable Cloud Messaging (FCM)
4. Go to Project Settings > Service Accounts > Generate new private key
5. Save the JSON file and set `FIREBASE_CREDENTIALS_PATH` to its absolute path

The `firebase-service-account.json` file is gitignored. Never commit it.

## API Endpoints

All endpoints are prefixed with `/api`. Port: `8095`.

### Push Notification

| Method | Path | Description |
|---|---|---|
| POST | `/api/notify` | Send FCM push notification to user's devices |

#### Request — `POST /api/notify`

```json
{
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "jobId": "660e8400-e29b-41d4-a716-446655440001",
    "status": "COMPLETED",
    "message": "Your build finished successfully"
}
```

This fetches device tokens from auth-service via Feign (`/api/auth/internal/device-token/{userId}`) and sends FCM push to each registered device.

### WebSocket Status Update

| Method | Path | Description |
|---|---|---|
| POST | `/api/notify/ws` | Push status update to connected WebSocket client |

#### Request — `POST /api/notify/ws`

```json
{
    "jobId": "660e8400-e29b-41d4-a716-446655440001",
    "step": "BUILD",
    "status": "IN_PROGRESS",
    "message": "Compiling sources...",
    "timestamp": "2026-07-15T20:00:00Z"
}
```

Only delivers if a WebSocket client is connected for the given `jobId`. Otherwise silently dropped.

### WebSocket Connection

| Protocol | Path | Description |
|---|---|---|
| WS | `/api/ws/jobs/{jobId}` | Connect to receive real-time job status updates |

Connect with JWT token as query parameter:

```
ws://localhost:8095/api/ws/jobs/{jobId}?token=<JWT>
```

Test with wscat:

```bash
npx wscat -c "ws://localhost:8095/api/ws/jobs/660e8400-e29b-41d4-a716-446655440001?token=YOUR_JWT"
```

## Architecture

```
┌─────────────┐     POST /api/notify      ┌──────────────────────┐
│  Caller     │ ──────────────────────────>│  NotifyController    │
│  (other     │                            │                      │
│  services)  │     POST /api/notify/ws    │  sendPush()          │
│             │ ──────────────────────────>│  sendWebSocketUpdate()│
└─────────────┘                            └──────┬───────┬───────┘
                                                  │       │
                                    ┌─────────────┘       └──────────────┐
                                    v                                    v
                        ┌───────────────────┐              ┌─────────────────────┐
                        │ NotificationService│              │ WebSocketSession    │
                        │                   │              │ Registry            │
                        │ Feign → auth-svc  │              │                     │
                        │ FCM push send     │              │ jobId → session map │
                        └───────────────────┘              └─────────────────────┘
                                    │                                    ^
                                    v                                    │
                        ┌───────────────────┐              ┌─────────────────────┐
                        │ Firebase Cloud    │              │ WebSocket clients   │
                        │ Messaging         │              │ ws://.../jobs/{id}  │
                        └───────────────────┘              └─────────────────────┘
```

## Inter-Service Communication

| Target | Method | Path | Purpose |
|---|---|---|---|
| auth-service | GET | `/api/auth/internal/device-token/{userId}` | Fetch registered FCM device tokens |

Feign client with configurable URL. Defaults to `http://localhost:8085` for local dev. In production with service discovery, leave `AUTH_SERVICE_URL` empty to use load-balanced lookup.

## Security

- REST endpoints (`/api/notify/**`) are `permitAll` — intended for internal service-to-service calls
- WebSocket handler validates JWT from query parameter
- CSRF disabled (stateless API)
- Session management: stateless

## Run the Service

From this directory:

```bash
mvn spring-boot:run
```

Or from the repo root:

```bash
mvn -f services/notification-service/pom.xml spring-boot:run
```

## Testing

### 1. WebSocket (test first — no external dependencies)

```bash
# Connect
npx wscat -c "ws://localhost:8095/api/ws/jobs/JOB_UUID?token=JWT"

# Then in another terminal, push update
curl -X POST http://localhost:8095/api/notify/ws \
  -H "Content-Type: application/json" \
  -d '{"jobId":"JOB_UUID","step":"BUILD","status":"IN_PROGRESS","message":"Compiling...","timestamp":"2026-07-15T20:00:00Z"}'
```

### 2. Push Notification (requires auth-service + Firebase)

```bash
curl -X POST http://localhost:8095/api/notify \
  -H "Content-Type: application/json" \
  -d '{"userId":"USER_UUID","jobId":"JOB_UUID","status":"COMPLETED","message":"Build done"}'
```

Requires:
- Auth service running with device tokens registered
- Valid Firebase credentials
- Real FCM device token (not a test string)
