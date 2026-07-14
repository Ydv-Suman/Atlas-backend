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
GITHUB_CLIENT_ID=your_github_oauth_client_id
GITHUB_CLIENT_SECRET=your_github_oauth_client_secret
ENCRYPTION_KEY=your_hex_encryption_key
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

All endpoints use media-type versioning: append `?v=1.0` to the URL.

### Public (no auth required)

| Method | Path | Description |
|---|---|---|
| POST | `/api/users/register/public?v=1.0` | Register new user |
| POST | `/api/auth/login?v=1.0` | Login, returns JWT |
| POST | `/api/users/verify-email?v=1.0` | Verify email with OTP |
| POST | `/api/users/resend-otp?v=1.0` | Resend OTP |
| GET | `/api/csrf/public` | Get CSRF token |
| GET | `/api/github/callback` | GitHub OAuth callback (receives code + state from GitHub redirect) |

### Authenticated (Bearer token required)

| Method | Path | Description |
|---|---|---|
| GET | `/api/users/fetch?v=1.0` | Get current user profile |
| PUT | `/api/users/update?v=1.0` | Update user profile |
| DELETE | `/api/users/delete?v=1.0` | Delete current user |
| POST | `/api/auth/logout?v=1.0` | Logout (blacklists token) |
| POST | `/api/github/authorize?v=1.0` | Returns GitHub OAuth authorization URL |

### Admin only

| Method | Path | Description |
|---|---|---|
| POST | `/api/users/register/admin?v=1.0` | Register admin user |

### Internal (service-to-service, no JWT)

| Method | Path | Description |
|---|---|---|
| GET | `/api/github/internal/token/{username}` | Returns decrypted GitHub token for user |

### Request / Response Bodies

#### Register — `POST /api/users/register/public?v=1.0`

```json
{
    "firstName": "John",
    "lastName": "Doe",
    "username": "john_doe",
    "email": "john@example.com",
    "password": "Password@123"
}
```

#### Login — `POST /api/auth/login?v=1.0`

```json
{
    "username": "john_doe",
    "password": "Password@123"
}
```

Response:

```json
{
    "token": "eyJhbGciOiJIUzI1NiJ9..."
}
```

#### Verify Email — `POST /api/users/verify-email?v=1.0`

```json
{
    "email": "john@example.com",
    "otp": "123456"
}
```

#### Resend OTP — `POST /api/users/resend-otp?v=1.0`

```json
{
    "email": "john@example.com"
}
```

#### Update User — `PUT /api/users/update?v=1.0`

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

#### GitHub Authorize — `POST /api/github/authorize?v=1.0`

No request body. Returns:

```json
{
    "authorizationUrl": "https://github.com/login/oauth/authorize?client_id=...&state=..."
}
```

## Database Schema

Flyway manages schema migrations in `src/main/resources/db/migration/`:

- `v1__create_user_schema.sql` — `users` table: `id`, `first_name`, `middle_name`, `last_name`, `username`, `email`, `hashed_password`, `role`, `tier`, `email_verified`, `github_authorized`, `created_at`, `updated_at`
- `v2__create_github_connections.sql` — `github_connections` table: `id`, `user_id`, `github_username`, `encrypted_access_token`, `scope`, `authorized_at`

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
1. POST /api/users/register/public?v=1.0   → 201 (async OTP email sent)
2. POST /api/users/verify-email?v=1.0      → 200 (email_verified=true)
3. POST /api/auth/login?v=1.0              → 200 + JWT token
4. Use JWT in Authorization: Bearer <token> for all authenticated endpoints
```

### GitHub Authorization Flow

```
1. User completes registration + email verification above
2. POST /api/github/authorize?v=1.0 (with JWT) → returns GitHub OAuth URL
3. User opens URL → logs into GitHub → clicks Authorize
4. GitHub redirects to GET /api/github/callback?code=...&state=...
5. auth-service exchanges code for token, stores encrypted token, sets github_authorized=true
6. User now passes onboarding gate (email_verified + github_authorized)
```

### Account Update Flow

```
Profile only:  PUT /api/users/update?v=1.0  { "firstName": "New" }
Email change:  PUT /api/users/update?v=1.0  { "email": "new@mail.com" }
               → resets emailVerified, sends new OTP, must re-verify
Password:      PUT /api/users/update?v=1.0  { "currentPassword": "old", "password": "new" }
```

## Notes

- `createdAt` and `updatedAt` are handled by entity lifecycle methods.
- Error messages are generic to prevent user/email enumeration.
- OTP comparison uses timing-safe `MessageDigest.isEqual()`.
- Async OTP email retries 3 times with exponential backoff. If all fail, user can call `/resend-otp`.
