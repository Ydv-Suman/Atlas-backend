# GitHub Service

Spring Boot service for GitHub OAuth authorization, token management, and repository operations.

## Requirements

- Java 25
- Maven
- PostgreSQL (Neon cloud-hosted)
- GitHub OAuth App (registered at github.com → Settings → Developer Settings → OAuth Apps)

## Configuration

Copy `.env.example` to `.env` in this directory and fill in the values:

```properties
DB_URL=jdbc:postgresql://<neon-host>/<dbname>?sslmode=require
DB_USERNAME=neondb_owner
DB_PASSWORD=your_neon_password
GITHUB_CLIENT_ID=your_github_oauth_client_id
GITHUB_CLIENT_SECRET=your_github_oauth_client_secret
ENCRYPTION_KEY=your_32_char_hex_key
AUTH_SERVICE_URL=http://localhost:8090
```

`application.yml` reads these values from environment variables or from `.env`:

```yaml
spring:
  config:
    import: optional:file:.env[.properties],optional:file:services/github-service/.env[.properties]
```

If you run the service from the repo root, Spring can read `services/github-service/.env`.
If you run it from inside `services/github-service`, it can read the local `.env`.

## GitHub OAuth App Setup

1. Go to GitHub → Settings → Developer Settings → OAuth Apps → New OAuth App
2. Fill in:
   - **Application name**: `atlas`
   - **Homepage URL**: `http://localhost:8080`
   - **Authorization callback URL**: `http://localhost:9000/api/github/callback`
3. After creation, copy **Client ID** and generate a **Client Secret**
4. Add both to `.env`

### Required Scopes

| Scope | Purpose |
|---|---|
| `repo` | Full access to public and private repositories (clone, push, PR) |
| `read:user` | Read GitHub username and profile |
| `workflow` | Inject and trigger GitHub Actions workflows |

## API Endpoints

### OAuth Flow

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/github/authorize` | Headers | Initiate GitHub OAuth. Returns authorization URL. |
| GET | `/api/github/callback` | None (browser redirect) | OAuth callback. Exchanges code for token, stores encrypted token, notifies auth-service. |

### POST `/api/github/authorize`

Initiates GitHub OAuth flow. Returns a URL the mobile app opens in an in-app browser.

**Request Headers:**

| Header | Required | Description |
|---|---|---|
| `X-User-Id` | Yes | Atlas user UUID |
| `X-User-Email` | Yes | Atlas user email |
| `Accept` | Yes | `application/vnd.atlasapp+json; v=1.0` |

**Response:** `200 OK`

```json
{
  "authorizationUrl": "https://github.com/login/oauth/authorize?client_id=...&redirect_uri=...&scope=repo,read:user,workflow&state=..."
}
```

**Error:** `401 Unauthorized` — missing or blank headers.

### GET `/api/github/callback`

GitHub redirects here after user approves. **Not called by the mobile app directly.**

**Query Parameters:**

| Param | Source | Description |
|---|---|---|
| `code` | GitHub | One-time authorization code |
| `state` | GitHub | State parameter (echoed back from authorize step) |

**Response:** `200 OK`

```json
{
  "statusCode": "200",
  "statusMessage": "GitHub connected successfully. You can close this window."
}
```

**Side Effects:**
1. Exchanges `code` for GitHub access token (server-to-server)
2. Fetches GitHub username via `GET https://api.github.com/user`
3. Encrypts access token with AES/GCM, stores in `github_connections` table
4. Calls auth-service `POST /api/auth/github-authorized?email=...` to set `github_authorized=true`

## Workflow

### Complete GitHub Authorization Flow

```
Mobile App                    github-service                 GitHub.com               auth-service
    |                              |                            |                         |
    |-- POST /api/github/authorize |                            |                         |
    |   (X-User-Id, X-User-Email)  |                            |                         |
    |<-- { authorizationUrl }       |                            |                         |
    |                              |                            |                         |
    |-- Opens URL in browser ------|--------------------------->|                         |
    |                              |   User clicks "Authorize"  |                         |
    |                              |<-- GET /callback?code&state |                         |
    |                              |                            |                         |
    |                              |-- POST /login/oauth/       |                         |
    |                              |   access_token             |                         |
    |                              |<-- { access_token }        |                         |
    |                              |                            |                         |
    |                              |-- GET /user (Bearer token) |                         |
    |                              |<-- { login: "username" }   |                         |
    |                              |                            |                         |
    |                              |-- Encrypt token, save DB   |                         |
    |                              |                            |                         |
    |                              |-- POST /api/auth/github-authorized?email=... ------->|
    |                              |                            |      github_authorized  |
    |                              |                            |      = true             |
    |                              |                            |                         |
    |<-- 200 "Connected" ---------|                            |                         |
```

### State Parameter (CSRF Protection)

The `state` parameter prevents CSRF attacks during OAuth flow.

**Format:** `base64url(userId|email|timestamp|hmac_sha256)`

- **userId**: Atlas user identifier
- **email**: Atlas user email (used to notify auth-service)
- **timestamp**: Unix epoch seconds (10-minute expiry)
- **hmac_sha256**: HMAC signature using GitHub client secret as key

On callback, the service:
1. Decodes and splits the state
2. Recomputes HMAC and compares (prevents tampering)
3. Checks timestamp (rejects if > 10 minutes old)
4. Extracts userId to store connection, email to notify auth-service

## Security

### Token Encryption

GitHub access tokens are encrypted at rest using AES/GCM:

| Property | Value |
|---|---|
| Algorithm | AES/GCM/NoPadding |
| Key size | 128-bit (from 32-char hex key) |
| IV | 12 bytes, randomly generated per encryption |
| Auth tag | 128-bit |
| Storage format | Base64(IV + ciphertext + auth_tag) |

### Security Properties

- GitHub access tokens **never** sent to the mobile app
- Client secret stays server-side — code exchange is server-to-server
- State parameter signed with HMAC-SHA256 to prevent CSRF
- State expires after 10 minutes to prevent replay
- Re-authorization replaces existing connection (deletes old record)

## Database Schema

Flyway creates the `github_connections` table:

```sql
CREATE TABLE IF NOT EXISTS github_connections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL,
    github_username VARCHAR(100) NOT NULL,
    encrypted_access_token TEXT NOT NULL,
    scope VARCHAR(255) NOT NULL,
    authorized_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

| Column | Type | Description |
|---|---|---|
| `id` | UUID | Primary key, auto-generated |
| `user_id` | VARCHAR(255) | Atlas user identifier |
| `github_username` | VARCHAR(100) | GitHub username |
| `encrypted_access_token` | TEXT | AES/GCM encrypted GitHub token |
| `scope` | VARCHAR(255) | Granted GitHub permissions |
| `authorized_at` | TIMESTAMP | When authorization occurred |

## Cross-Service Communication

### Outbound (Feign)

| Target | Endpoint | Purpose |
|---|---|---|
| auth-service (`AUTH_SERVICE_URL`, default `http://localhost:8090`) | `POST /api/auth/github-authorized?email={email}` | Mark user as GitHub-authorized after successful OAuth |

## Run the Service

From this directory:

```bash
./mvnw spring-boot:run
```

Or from the repo root:

```bash
mvn -f services/github-service/pom.xml spring-boot:run
```

## Testing with Postman

### Step 1 — Get Authorization URL

```
POST http://localhost:9000/api/github/authorize

Headers:
  X-User-Id: <your-atlas-user-uuid>
  X-User-Email: <your-email>
  Accept: application/vnd.atlasapp+json; v=1.0
```

### Step 2 — Authorize on GitHub

Copy `authorizationUrl` from response, open in browser. Click "Authorize".

GitHub redirects to callback automatically. You should see:

```
GitHub connected successfully. You can close this window.
```

### Step 3 — Verify

```
GET http://localhost:8090/api/users/fetch

Headers:
  Authorization: Bearer <your-jwt>
  Accept: application/vnd.atlasapp+json; v=1.0
```

Confirm `githubAuthorized: true` in response.

## Notes

- Callback endpoint has no API versioning — it's hit by browser redirect, not the mobile app.
- GitHub authorization codes are single-use and expire after 10 minutes.
- If re-authorizing, the old connection record is deleted and replaced.
- The `ENCRYPTION_KEY` must be exactly 32 hex characters (16 bytes / AES-128).
