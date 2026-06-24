# Auth Service

Spring Boot service for authentication and user management.

## Requirements

- Java 25
- Maven
- Redis (Docker)
- Neon PostgreSQL (cloud-hosted)

## Configuration

Copy `.env.example` to `.env` in this directory and fill in the values:

```properties
DB_URL=jdbc:postgresql://<neon-host>/<dbname>?sslmode=require
DB_USERNAME=neondb_owner
DB_PASSWORD=your_neon_password
JWT_SECRET=your_jwt_secret
JWT_EXPIRATION_MS=86400000
REDIS_HOST=localhost
REDIS_PORT=6379
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your_email@gmail.com
MAIL_PASSWORD=your_app_password
```

`application.yml` reads these values from environment variables or from `.env`:

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

## Infrastructure

### Redis (Docker)

```bash
docker run -d --name redis -p 6379:6379 redis:8.2.7
```

Or via docker compose:

```bash
docker compose -f services/auth-service/docker-compose.yml up redis -d
```

### PostgreSQL (Neon)

Database is hosted on [Neon](https://neon.tech). No local PostgreSQL container needed.

Connection string goes in `.env` as `DB_URL` with `?sslmode=require`.

### Redis Usage

Redis is used for:

| Key pattern | Purpose | TTL |
|---|---|---|
| `otp:<email>` | Stores 6-digit OTP for email verification | 15 min |
| `otp_attempts:<email>` | Tracks OTP verification attempts (max 5) | 15 min |
| `otp_cooldown:<email>` | Prevents OTP resend spam | 60 sec |
| `otp_resend_count:<email>` | Limits OTP resend cycles (max 5) | 1 hour |
| `login_attempts:<username>` | Tracks failed login attempts (max 10) | 15 min |
| `blacklisted_token:<jwt>` | Stores blacklisted JWT tokens after logout | JWT expiry (24h default) |

## Security

### Authentication

- Stateless JWT (HS256) in `Authorization: Bearer <token>` header
- BCrypt password hashing with cost factor 12
- CSRF protection via double-submit cookie pattern for non-public endpoints
- Logout invalidates JWT via Redis token blacklist

### Rate Limiting

- **Login**: 10 failed attempts per username → 15-minute lockout
- **OTP verification**: 5 attempts per OTP code
- **OTP resend**: 60-second cooldown between resends, max 5 resend cycles per hour

### Password Policy

- Minimum 8 characters
- Requires uppercase, lowercase, digit, and special character
- Password changes require `currentPassword` verification

### Email Verification

- Registration sends 6-digit OTP to email (async with 3 retries)
- User must verify email before login is allowed
- Changing email resets `emailVerified` and sends new OTP (sync)

### Security Headers

- `X-Frame-Options: DENY`
- `Strict-Transport-Security: max-age=31536000; includeSubDomains`
- `X-Content-Type-Options: nosniff`
- `Content-Security-Policy: default-src 'self'`
- `Permissions-Policy: geolocation=(), microphone=(), camera=()`
- `Cache-Control: no-cache, no-store`
- `Referrer-Policy: no-referrer`

### Roles

- `ROLE_USER` — standard user, created via `/users/register/public`
- `ROLE_ADMIN` — admin, created via `/users/register/admin` (requires ADMIN role) or bootstrap on first startup

## API Endpoints

### Public (no auth required)

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/users/register/public` | Register new user |
| POST | `/api/v1/auth/login` | Login, returns JWT |
| POST | `/api/v1/users/verify-email` | Verify email with OTP |
| POST | `/api/v1/users/resend-otp` | Resend OTP |
| GET | `/api/v1/csrf/public` | Get CSRF token |

### Authenticated (Bearer token required)

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/users/fetch` | Get current user profile |
| PUT | `/api/v1/users/update` | Update user profile |
| DELETE | `/api/v1/users/delete` | Delete current user |
| POST | `/api/v1/auth/logout` | Logout (blacklists token) |

### Internal (service-to-service, no auth required)

| Method | Path | Description |
|---|---|---|
| POST | `/api/auth/github-authorized?email={email}` | Set `github_authorized=true` for user. Called by github-service after OAuth callback. |

### Admin only

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/users/register/admin` | Register admin user |

### Update User Request

Password change requires `currentPassword`:

```json
{
    "firstName": "John",
    "lastName": "Doe",
    "username": "john_doe",
    "email": "john@example.com",
    "currentPassword": "OldPassword@123",
    "password": "NewPassword@456"
}
```

Omit `password` and `currentPassword` to update only profile fields.

## Database Schema

Flyway creates the `users` table in:

- [`src/main/resources/db/migration/V1__user_schema.sql`](src/main/resources/db/migration/V1__user_schema.sql)

Columns: `id`, `first_name`, `middle_name`, `last_name`, `username`, `email`, `hashed_password`, `role`, `tier` (`FREE`/`PRO`), `email_verified`, `github_authorized`, `created_at`, `updated_at`.

## Run Flyway

Flyway runs automatically on application startup. To run manually:

```bash
DB_URL="jdbc:postgresql://<neon-host>/<dbname>?sslmode=require" \
DB_USERNAME="neondb_owner" \
DB_PASSWORD="your_password" \
./mvnw flyway:migrate
```

Useful commands:
- `flyway:info` — shows applied and pending migrations
- `flyway:validate` — checks migration consistency
- `flyway:migrate` — applies pending migrations

## Run the Service

From this directory:

```bash
./mvnw spring-boot:run
```

Or from the repo root:

```bash
mvn -f services/auth-service/pom.xml spring-boot:run
```

## Admin Bootstrap

On first startup, if no admin exists and env vars are set, an admin user is auto-created with `emailVerified=true`:

```properties
DEFAULT_ADMIN_FIRST_NAME=Admin
DEFAULT_ADMIN_LAST_NAME=User
DEFAULT_ADMIN_USERNAME=admin
DEFAULT_ADMIN_EMAIL=admin@example.com
DEFAULT_ADMIN_PASSWORD=SecurePassword@123
```

## Workflow

### Registration → Login Flow

```
1. POST /api/users/register/public     → 201 (async OTP email sent)
2. POST /api/users/verify-email        → 200 (email_verified=true)
3. POST /api/auth/login                → 200 + JWT token
4. Use JWT in Authorization: Bearer <token> for all authenticated endpoints
```

### GitHub Authorization Flow (cross-service)

```
1. User completes registration + email verification above
2. github-service handles OAuth flow (see github-service README)
3. github-service calls POST /api/auth/github-authorized?email=user@example.com
4. auth-service sets github_authorized=true on user record
5. User now passes onboarding gate (email_verified + github_authorized)
```

### Account Update Flow

```
Profile only:  PUT /api/users/update  { "firstName": "New" }
Email change:  PUT /api/users/update  { "email": "new@mail.com" }
               → resets emailVerified, sends new OTP, must re-verify
Password:      PUT /api/users/update  { "currentPassword": "old", "password": "new" }
```

## Notes

- `createdAt` and `updatedAt` are handled by entity lifecycle methods.
- Error messages are generic to prevent user/email enumeration.
- OTP comparison uses timing-safe `MessageDigest.isEqual()`.
- Async OTP email retries 3 times with exponential backoff. If all fail, user can call `/resend-otp`.
