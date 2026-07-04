# Atlas Shared Library

Cross-service contracts for Atlas backend microservices. Contains only DTOs, exceptions, and JWT validation logic — **no business logic**.

## What's Inside

```
com.atlas.shared/
├── dto/
│   ├── ApiResponse<T>       — Standard success response wrapper (statusCode, message, data)
│   └── ErrorResponse        — Standard error response (apiPath, errorCode, errorMessage, errorTime)
├── exception/
│   ├── GlobalExceptionHandler — Base exception handler (extend + annotate @RestControllerAdvice)
│   ├── UnauthorizedException  — 401
│   ├── ForbiddenException     — 403
│   ├── ResourceNotFoundException — 404
│   └── InsufficientCreditsException — 402
└── security/
    ├── JwtClaims              — Record: email, roles, emailVerified, githubAuthorized, tier
    ├── JwtTokenParser         — Stateless JWT validation + claim extraction (no token generation)
    ├── JwtAuthenticationFilter — Generic filter; override isTokenAllowed() for extra checks
    └── JwtTokenParserConfig   — Auto-configured bean from app.jwt.secret property
```

## Usage

### 1. Add dependency

```xml
<dependency>
    <groupId>com.atlas</groupId>
    <artifactId>shared-lib</artifactId>
</dependency>
```

Version managed by parent POM — no version tag needed.

### 2. Set JWT secret

In your service's `application.yml`:

```yaml
app:
  jwt:
    secret: ${JWT_SECRET}
```

`JwtTokenParser` bean auto-configures via Spring Boot auto-configuration. No `@Import` needed.

### 3. Wire JWT filter

In your service's `SecurityConfig`:

```java
@Bean
public JwtAuthenticationFilter jwtFilter(JwtTokenParser parser) {
    return new JwtAuthenticationFilter(parser);
}
```

For extra validation (e.g. token blacklist), extend the filter:

```java
public class MyFilter extends JwtAuthenticationFilter {
    @Override
    protected boolean isTokenAllowed(String token) {
        return !blacklistService.isBlacklisted(token);
    }
}
```

### 4. Use DTOs

```java
// Success response
ApiResponse.success("200", "Created successfully");
ApiResponse.success("200", "Fetched", someData);

// Error response (used by GlobalExceptionHandler)
ErrorResponse.of(HttpStatus.NOT_FOUND, "User not found", "/api/v1/users/123");
```

### 5. Extend exception handler

```java
@RestControllerAdvice
public class MyExceptionHandler extends GlobalExceptionHandler {
    @ExceptionHandler(MyCustomException.class)
    public ResponseEntity<ErrorResponse> handle(MyCustomException ex, HttpServletRequest req) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), req.getRequestURI());
    }
}
```

Common handlers (validation, 401, 403, 404, 402, 500) inherited automatically.

## JWT Claims

The shared filter sets `authentication.getName()` = email, and `authentication.getDetails()` = `JwtClaims` record.

```java
// In any controller
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
String email = auth.getName();
JwtClaims claims = (JwtClaims) auth.getDetails();
boolean verified = claims.emailVerified();
String tier = claims.tier();
```

## Rules

- **Contracts only.** No business logic, no service-specific code.
- **No Spring Boot application class.** This is a plain JAR, not an executable.
- **Same JWT_SECRET across all services.** Auth-service signs, all services validate with same key.
