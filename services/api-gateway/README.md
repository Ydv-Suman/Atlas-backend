# API Gateway

Spring Cloud Gateway (WebFlux) — single entry point for all Atlas backend services. Handles JWT validation, route proxying, and security enforcement.

## Requirements

- Java 25
- Maven

## Configuration

Copy `.env.example` to `.env` in this directory and fill in the values:

```properties
JWT_SECRET=your_jwt_secret
AUTH_SERVICE_URL=http://localhost:8085
WORKSPACE_SERVICE_URL=http://localhost:8090
```

`JWT_SECRET` must match the secret used by auth-service for token signing.

`application.yaml` reads these values from environment variables or from `.env`:

```yaml
spring:
  config:
    import: optional:file:.env[.properties], optional:file:services/api-gateway/.env[.properties]
```

If you run the service from the repo root, Spring can read `services/api-gateway/.env`.
If you run it from inside `services/api-gateway`, it can read the local `.env`.

## Architecture

```
Client → API Gateway (:8080) → auth-service (:8085)
                              → workspace-service (:8090)
```

All client requests go through the gateway. In Docker, backend services are on an internal network — only the gateway port is exposed to the host.

## Routes

| Route ID | Predicate | Target |
|---|---|---|
| auth-service | `/api/auth/**`, `/api/github/**`, `/api/users/**` | auth-service |
| workspace-service | `/api/workspace/**` | workspace-service |

## Security

### Public Endpoints (no JWT required)

| Method | Path | Description |
|---|---|---|
| POST | `/api/auth/login` | Login |
| POST | `/api/users/register/public` | Register new user |
| POST | `/api/users/verify-email` | Verify email with OTP |
| POST | `/api/users/resend-otp` | Resend OTP |
| GET | `/api/github/callback` | GitHub OAuth callback |
| GET | `/api/csrf/public` | Get CSRF token |
| GET | `/actuator/**` | Health and metrics |

### Authenticated Endpoints (Bearer token required)

All other endpoints require a valid JWT in `Authorization: Bearer <token>` header.

### Security Layers

1. **JWT Authentication** — reactive `WebFilter` validates Bearer token, sets security context
2. **Downstream CSRF** — auth-service has its own CSRF protection for state-changing operations

### Network Isolation (Docker)

In Docker Compose, only the gateway exposes a host port (`8080`). Backend services use `expose` (internal network only), preventing direct access.

## Run

From this directory:

```bash
./mvnw spring-boot:run
```

Or from the repo root:

```bash
mvn -f services/api-gateway/pom.xml spring-boot:run
```

## Docker

Build from repo root:

```bash
docker build -t sumanydv/atlas-api-gateway:v3 -f services/api-gateway/Dockerfile .
```
