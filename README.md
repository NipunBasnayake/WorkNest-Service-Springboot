# WorkNest Service

![Java](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3.5-6DB33F?logo=springboot&logoColor=white)
![License](https://img.shields.io/badge/License-Proprietary-lightgrey)
![Build Status](https://img.shields.io/badge/Build-Passing-brightgreen)

Enterprise-grade, multi-tenant backend for the WorkNest platform.
Designed for secure platform onboarding, tenant data isolation, HR and project operations, and real-time collaboration.

---

## Overview

WorkNest Service is a Java 21 + Spring Boot 3.3.5 backend with strict separation between platform control-plane data and tenant business data.

The implementation provides:

- Master-plane tenant lifecycle and onboarding
- Stateless JWT authentication with rotating refresh tokens
- Tenant isolation enforced at HTTP filter, JWT claim, and Hibernate connection-provider layers
- HR, project execution, communication, analytics, and audit capabilities
- STOMP-over-WebSocket messaging for chat and notification fan-out

Production note: current repository defaults are development-oriented in several areas (secrets, ddl-auto, public onboarding, in-memory broker). See Production Warnings sections below before deployment.

---

## Technology Stack

| Layer | Technology | Current Implementation Notes |
|---|---|---|
| Runtime | Java 21 | Configured via Maven compiler and Spring Boot parent |
| Framework | Spring Boot 3.3.5 | REST, scheduling, async, actuator |
| Security | Spring Security 6 + JWT (JJWT) | Stateless bearer auth, role-based authorization, tenant claim checks |
| Data Access | Spring Data JPA + Hibernate 6 | Separate master and tenant persistence units |
| Multi-tenancy | Hibernate DATABASE multi-tenancy | Custom CurrentTenantIdentifierResolver + MultiTenantConnectionProvider |
| Database | MySQL 8.x | Master DB plus per-tenant DB credentials in platform metadata |
| Migrations | Hibernate ddl-auto | No Flyway dependency or migration scripts currently present |
| Real-time | Spring WebSocket + STOMP + SockJS | In-memory simple broker with heartbeat |
| API Docs | springdoc-openapi 2.6.0 | JWT bearer scheme in OpenAPI |
| Containerization | Docker + Docker Compose | Multi-stage image build |

---

## Architecture and Data Isolation

### Multi-tenant model

| Data Domain | Persistence Unit | Physical Storage | Responsibilities |
|---|---|---|---|
| Master control plane | masterEntityManagerFactory | `platform_master` (configured in `spring.datasource.*`) | Platform users, refresh tokens, tenant registry, onboarding metadata |
| Tenant business plane | entityManagerFactory (tenant) | Per-tenant MySQL databases (resolved from `platform_tenants.db_url/db_username/db_password`) | Employees, teams, attendance, leaves, projects, tasks, chats, notifications, audit logs |

### Request routing and tenant resolution

Tenant-scoped endpoints are all under `/api/tenant/**`.

1. `TenantContextFilter` runs first (`@Order(1)`) and enforces `X-Tenant-ID` for tenant endpoints.
2. Tenant key is normalized (`trim + lowercase`) and validated against master tenant metadata.
3. Non-existent tenants return `TENANT_NOT_FOUND`; inactive tenants return `TENANT_INACTIVE`.
4. Valid tenant is stored in `TenantContext` (`ThreadLocal`) and added to MDC for traceable logs.
5. Hibernate asks `CurrentTenantIdentifierResolverImpl` for the tenant identifier.
6. `MultiTenantConnectionProviderImpl` obtains or creates a tenant datasource from `TenantDataSourceServiceImpl`.
7. `TenantDataSourceServiceImpl` caches Hikari pools per tenant with scheduled idle eviction and max-cache controls.
8. `TenantContext` is cleared in `finally` to avoid cross-request leakage.

Tenant isolation guarantees in this implementation:

- Header gate: tenant endpoints require `X-Tenant-ID`.
- Metadata gate: tenant must exist and be ACTIVE in master registry.
- Token gate: JWT `tenantKey` must match authenticated user tenant.
- Request gate: for `/api/tenant/**`, `X-Tenant-ID` must match JWT `tenantKey`.
- Connection gate: tenant persistence rejects master/default tenant identifiers for tenant-scoped DB access.

### Startup bootstrap flow

Startup sequence is implemented with ordered `CommandLineRunner` components:

1. `MasterDatabaseStartupValidator` (`@Order(HIGHEST_PRECEDENCE)`) validates master DB connectivity.
2. `StartupSecretsValidator` (`@Order(5)`) enforces stronger checks when `prod` profile is active.
3. `BootstrapDataInitializer` (`@Order(10)`) creates a bootstrap `PLATFORM_ADMIN` when required.
4. `TenantSchemaInitializer` (`@Order(20)`) ensures schema for each ACTIVE tenant.
5. `DemoTenantDataSeeder` (`@Order(30)`, conditional) seeds demo tenant data when enabled.
6. `TenantAdminMirrorRepairInitializer` (`@Order(40)`) repairs tenant-admin employee mirrors.

Onboarding provisioning flow:

- `POST /api/platform/onboarding/tenants` accepts registration and emits `TenantProvisioningRequestedEvent`.
- Async listener (`tenantProvisioningExecutor`) creates DB if missing, applies tenant schema, mirrors tenant admin employee, then marks tenant ACTIVE.
- Provisioning failure marks tenant SUSPENDED.

Production Warnings:

- `POST /api/platform/onboarding/tenants` is currently publicly accessible (`permitAll`) and must be protected by at least one of: API gateway auth, invitation token, mTLS, allowlist, and request throttling.
- `spring.jpa.hibernate.ddl-auto` and `app.tenant.jpa.hibernate.ddl-auto` default to `update`; this is not deterministic schema management for production.

---

## Security and Access Control

### Role mapping

| Role | Scope | Typical Access |
|---|---|---|
| `PLATFORM_ADMIN` | Master/platform | `/api/platform/**`, platform announcements, tenant governance |
| `TENANT_ADMIN` | Tenant | Tenant-wide administration and operational controls |
| `ADMIN` | Tenant | Tenant administration equivalent for business operations |
| `MANAGER` | Tenant | Team/project management workflows |
| `HR` | Tenant | HR workflows, leave/attendance governance, HR chat |
| `EMPLOYEE` | Tenant | Self-service employee, task, communication actions |

### Public endpoints and authenticated surfaces

Configured public endpoints in security filter chain:

- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/forgot-password`
- `POST /api/auth/reset-password`
- `POST /api/platform/onboarding/tenants`
- `/ws/**` (handshake path is public; STOMP CONNECT is token-validated by interceptor)
- `/error`
- `/actuator/health` and `/actuator/health/**` when `app.security.public-health-enabled=true`
- `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html` when `app.security.swagger-public-enabled=true`

Authenticated route controls:

- `/api/platform/**` requires `ROLE_PLATFORM_ADMIN`
- `/api/tenant/**` requires any of `ROLE_TENANT_ADMIN`, `ROLE_ADMIN`, `ROLE_MANAGER`, `ROLE_HR`, `ROLE_EMPLOYEE`
- `/api/auth/logout`, `/api/auth/me`, `/api/auth/change-password`, `/api/auth/admin/**` require authentication

### Authentication flow (access + refresh)

1. Client calls `POST /api/auth/login` with email, password, and tenant key for tenant-scoped users.
2. Server validates user status, tenant scope, and password.
3. Existing active refresh tokens for that user are revoked.
4. Access token is issued (JWT HS256) with claims:
   - `sub`: user email
   - `uid`: user id
   - `role`: platform role
   - `tenantKey`: tenant key (null for platform admin)
5. Refresh token is generated as random opaque value; only SHA-256 hash is persisted in DB.
6. Client uses bearer access token for API calls.
7. For refresh, client calls `POST /api/auth/refresh` with refresh token and tenant key (payload/header consistency enforced).
8. Refresh token is rotated atomically; old token revoked with `rotatedToToken` hash linkage.
9. On logout, refresh token is validated and revoked; authenticated principal must match token owner.

### WebSocket/STOMP security

- `/ws` endpoint uses SockJS and allowed origin patterns from `app.websocket.allowed-origins`.
- STOMP `CONNECT` must include:
  - `Authorization: Bearer <access-token>`
  - `X-Tenant-ID` (tenant-scoped users)
- Interceptor validates:
  - Token signature and expiry
  - JWT tenant claim == principal tenant
  - STOMP tenant header == token tenant
  - Destination tenant path binding (`/topic/tenant/{tenantKey}/...`, `/app/tenant/{tenantKey}/...`)
  - Chat membership for team/HR conversation destinations

Production Warnings:

- CSRF is disabled globally. This is acceptable for pure stateless token APIs, but do not introduce cookie-based auth without re-evaluating CSRF.
- No built-in rate limiting, brute-force controls, or IP throttling are implemented.
- Onboarding endpoint is public by default and represents high abuse risk.
- Review `swagger-public-enabled` and `public-health-enabled` defaults before internet exposure.

---

## API Modules

| Module | Key Endpoints | Description |
|---|---|---|
| Authentication and Identity | `/api/auth/login`, `/api/auth/refresh`, `/api/auth/logout`, `/api/auth/me`, `/api/auth/forgot-password`, `/api/auth/reset-password`, `/api/auth/change-password` | JWT auth, rotating refresh tokens, account password lifecycle |
| Platform Onboarding and Governance | `/api/platform/onboarding/tenants`, `/api/platform/tenants/**`, `/api/platform/announcements/**` | Tenant registration/provisioning and platform governance |
| Employee and Organization (HR Core) | `/api/tenant/employees/**`, `/api/tenant/teams/**`, `/api/tenant/attendance/**`, `/api/tenant/leaves/**` | Employee records, team structures, attendance, leave workflows |
| Project Delivery | `/api/tenant/projects/**`, `/api/tenant/tasks/**` | Project planning, team assignment, task tracking, comments, kanban data |
| Communication | `/api/tenant/announcements/**`, `/api/tenant/notifications/**`, `/api/tenant/chats/team/**`, `/api/tenant/chats/hr/**`, `/api/tenant/chats/read-receipts/**` | Tenant announcements, notifications, team and HR chat |
| Analytics and Insights | `/api/tenant/dashboard/**`, `/api/tenant/analytics/**` | Operational dashboards and analytical summaries |
| Audit and Governance | `/api/tenant/audit-logs/**` | Auditable user and domain activity views |
| Attachments and Settings | `/api/tenant/attachments/**`, `/api/tenant/settings/**`, `/api/files/upload` | Attachment metadata/storage and tenant workspace configuration |

---

## API Quick Start

### 1) Login

```bash
curl -X POST "http://localhost:8080/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "platform.admin@worknest.local",
    "password": "ChangeMe123!",
    "tenantKey": null
  }'
```

### 2) Call a tenant endpoint

```bash
curl -X GET "http://localhost:8080/api/tenant/dashboard/me" \
  -H "Authorization: Bearer <access-token>" \
  -H "X-Tenant-ID: <tenant-key>"
```

### 3) Refresh token (rotation)

```bash
curl -X POST "http://localhost:8080/api/auth/refresh" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: <tenant-key>" \
  -d '{
    "refreshToken": "<refresh-token>",
    "tenantKey": "<tenant-key>"
  }'
```

### 4) Logout and revoke refresh token

```bash
curl -X POST "http://localhost:8080/api/auth/logout" \
  -H "Authorization: Bearer <access-token>" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: <tenant-key>" \
  -d '{
    "refreshToken": "<refresh-token>",
    "tenantKey": "<tenant-key>"
  }'
```

---

## Project Structure

```text
src/main/java/com/worknest
  auth/
    controller/           # Auth endpoints
    dto/                  # Auth request/response payloads
    service/              # Auth business services
  common/
    api/                  # ApiResponse/ErrorResponse wrappers
    enums/                # Shared enums (roles, status)
    exception/            # GlobalExceptionHandler and custom exceptions
    storage/              # Attachment/file abstractions
    util/                 # Shared constants/helpers
  config/
    SecurityConfig.java
    CorsConfig.java
    WebSocketConfig.java
    MasterDataSourceConfig.java
    MasterJpaConfig.java
    TenantHibernateConfig.java
    BootstrapDataInitializer.java
    TenantSchemaInitializer.java
    DemoTenantDataSeeder.java
    StartupSecretsValidator.java
  controller/             # Tenant REST controllers (HR, project, communication, analytics)
  master/
    controller/           # Platform admin/onboarding APIs
    dto/
    entity/               # PlatformTenant, PlatformUser, RefreshToken
    event/ listener/      # Async tenant provisioning flow
    repository/
    service/
  notification/
    email/                # Email notification services
  security/
    authorization/
    filter/               # JWT servlet filter + STOMP interceptor
    handler/              # Authentication/authorization handlers
    jwt/                  # JWT service
    model/                # Principal model
    service/ util/
  tenant/
    connection/           # MultiTenantConnectionProvider
    context/              # TenantContext + MasterTenantContextRunner
    datasource/           # Tenant datasource caching and lifecycle
    dto/
    entity/
    enums/
    filter/               # TenantContextFilter
    repository/
    resolver/             # CurrentTenantIdentifierResolver
    service/

src/main/resources
  application.yml

docker-compose.yml
Dockerfile
pom.xml
```

---

## Getting Started

### Prerequisites

- JDK 21
- Maven 3.9+
- MySQL 8+
- Docker and Docker Compose (optional)

### Run locally

1. Create a MySQL database for master metadata (for example: `platform_master`).
2. Update configuration values (recommended via environment overrides; see Environment Variables section).
3. Start the service:

```bash
mvn spring-boot:run
```

or

```bash
mvn clean package -DskipTests
java -jar target/worknest-service-0.0.1-SNAPSHOT.jar
```

Default application port is `8080`.

### Profiles

- Default profile: no explicit profile required.
- Production profile: `SPRING_PROFILES_ACTIVE=prod`.
- In `prod`, `StartupSecretsValidator` enforces non-empty critical secrets and blocks `spring.jpa.hibernate.ddl-auto=update`.

### Docker

```bash
docker compose up --build
```

Container endpoints:

- Backend: `http://localhost:${BACKEND_PORT:-8080}`
- MySQL: `localhost:${MYSQL_PORT:-3306}`

Production Warnings:

- Current `docker-compose.yml` exports `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`, while Spring Boot datasource properties are `spring.datasource.*`. Use `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD` (or map values inside `application.yml`) to avoid startup mismatches.
- Current repository `application.yml` contains hardcoded credentials and a JWT secret intended for local development only.

---

## Demo Data Seeding

Demo seeding is implemented by `DemoTenantDataSeeder` and only runs when enabled.

Required configuration:

- `bootstrap.seed-demo-data=true`
- `bootstrap.demo-user-password=<non-empty-password>`

Behavior:

- Runs after tenant schema initialization.
- Seeds only ACTIVE tenants.
- Skips a tenant if employees already exist.
- Creates demo employees, teams, projects, tasks, announcements, notifications, and chat conversations.
- Creates/links corresponding platform users for seeded tenant employees.

---

## API Documentation and WebSockets

### OpenAPI

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

Note: public exposure is controlled by `app.security.swagger-public-enabled`.

### WebSockets (STOMP)

- Handshake endpoint: `/ws` (SockJS enabled)
- Broker prefixes:
  - Application destinations: `/app`
  - Topic/queue broker destinations: `/topic`, `/queue`
  - User destination prefix: `/user`
- Tenant destination convention: `/topic/tenant/{tenantKey}/...` and `/app/tenant/{tenantKey}/...`
- Inbound/outbound channels are backed by task executors; simple broker heartbeat is `10s/10s`.

Production Warnings:

- The current broker is Spring in-memory simple broker, suitable for single-instance deployments.
- For horizontal scale and durable messaging, replace with an external broker relay (for example RabbitMQ STOMP relay) and externalize session routing/state as needed.

---

## Environment Variables

This project currently keeps many defaults directly in `application.yml`. For production, externalize all secrets and operational settings.

### Core runtime and profile

| Purpose | Spring Property | Recommended Environment Variable |
|---|---|---|
| Active profile | `spring.profiles.active` | `SPRING_PROFILES_ACTIVE` |
| Server port | `server.port` | `SERVER_PORT` |

### Master database

| Purpose | Spring Property | Recommended Environment Variable |
|---|---|---|
| Master JDBC URL | `spring.datasource.url` | `SPRING_DATASOURCE_URL` |
| Master DB username | `spring.datasource.username` | `SPRING_DATASOURCE_USERNAME` |
| Master DB password | `spring.datasource.password` | `SPRING_DATASOURCE_PASSWORD` |
| Driver class | `spring.datasource.driver-class-name` | `SPRING_DATASOURCE_DRIVER_CLASS_NAME` |

### JWT and tenant request context

| Purpose | Spring Property | Recommended Environment Variable |
|---|---|---|
| JWT signing secret (Base64) | `app.jwt.secret` | `APP_JWT_SECRET` |
| Access token TTL (ms) | `app.jwt.expiration` | `APP_JWT_EXPIRATION` |
| Refresh token TTL (ms) | `app.jwt.refresh-expiration` | `APP_JWT_REFRESH_EXPIRATION` |
| Tenant header name | `app.tenant.header` | `APP_TENANT_HEADER` |
| Default tenant alias | `app.tenant.default` | `APP_TENANT_DEFAULT` |

### CORS and WebSocket origins

| Purpose | Spring Property | Recommended Environment Variable |
|---|---|---|
| REST allowed origins | `app.cors.allowed-origins` | `APP_CORS_ALLOWED_ORIGINS` |
| WS allowed origins | `app.websocket.allowed-origins` | `APP_WEBSOCKET_ALLOWED_ORIGINS` |

### Security exposure toggles

| Purpose | Spring Property | Recommended Environment Variable |
|---|---|---|
| Public health endpoint | `app.security.public-health-enabled` | `APP_SECURITY_PUBLIC_HEALTH_ENABLED` |
| Public Swagger endpoints | `app.security.swagger-public-enabled` | `APP_SECURITY_SWAGGER_PUBLIC_ENABLED` |

### Mail and password reset

| Purpose | Spring Property | Recommended Environment Variable |
|---|---|---|
| SMTP host | `spring.mail.host` | `SPRING_MAIL_HOST` |
| SMTP port | `spring.mail.port` | `SPRING_MAIL_PORT` |
| SMTP username | `spring.mail.username` | `SPRING_MAIL_USERNAME` |
| SMTP password | `spring.mail.password` | `SPRING_MAIL_PASSWORD` |
| Sender email | `app.email.from` | `APP_EMAIL_FROM` |
| Reset token expiry minutes | `app.auth.password-reset.token-expiry-minutes` | `APP_AUTH_PASSWORD_RESET_TOKEN_EXPIRY_MINUTES` |
| Reset link base URL | `app.auth.password-reset.link-base-url` | `APP_AUTH_PASSWORD_RESET_LINK_BASE_URL` |

### Bootstrap and demo seed controls

| Purpose | Spring Property | Recommended Environment Variable |
|---|---|---|
| Enable bootstrap platform admin | `bootstrap.platform-admin.enabled` | `BOOTSTRAP_PLATFORM_ADMIN_ENABLED` |
| Bootstrap admin name | `bootstrap.platform-admin.name` | `BOOTSTRAP_PLATFORM_ADMIN_NAME` |
| Bootstrap admin email | `bootstrap.platform-admin.email` | `BOOTSTRAP_PLATFORM_ADMIN_EMAIL` |
| Bootstrap admin password | `bootstrap.platform-admin.password` | `BOOTSTRAP_PLATFORM_ADMIN_PASSWORD` |
| Enable demo seeding | `bootstrap.seed-demo-data` | `BOOTSTRAP_SEED_DEMO_DATA` |
| Demo user password | `bootstrap.demo-user-password` | `BOOTSTRAP_DEMO_USER_PASSWORD` |

### File storage and upload limits

| Purpose | Spring Property | Recommended Environment Variable |
|---|---|---|
| Frontend upload directory | `app.storage.frontend-public-uploads-dir` | `APP_STORAGE_FRONTEND_PUBLIC_UPLOADS_DIR` |
| Max upload size (bytes) | `app.storage.max-file-size-bytes` | `APP_STORAGE_MAX_FILE_SIZE_BYTES` |
| Allowed MIME types | `app.storage.allowed-mime-types` | `APP_STORAGE_ALLOWED_MIME_TYPES` |

Production Warnings:

- Manage secrets through a centralized secret manager (AWS Secrets Manager, Azure Key Vault, GCP Secret Manager, or HashiCorp Vault).
- Never commit real credentials, JWT keys, or SMTP app passwords.
- Rotate all secrets that were previously stored in repository history.

Error response standardization note:

- Most exceptions use `ErrorResponse` via `GlobalExceptionHandler`, and success responses use `ApiResponse`.
- JWT and tenant filters also write error payloads directly. Keep their error-code taxonomy aligned with global handler contracts to preserve client-side consistency.

---

## Phase Coverage

- Phase 1: Multi-tenant infrastructure (master/tenant separation, resolver/provider/context/filter, onboarding provisioning).
- Phase 2: Authentication and authorization (JWT access tokens, refresh token rotation/revocation, tenant-bound auth checks).
- Phase 3: HR and delivery core (employees, teams, attendance, leaves, projects, tasks).
- Phase 4: Communication and governance (announcements, notifications, team/HR chats, read receipts, audit logs, attachments).
- Phase 5: Operational hardening and delivery readiness (dashboard/analytics APIs, actuator integration, OpenAPI, Dockerization, async provisioning/seeding workflows).

Current production hardening gaps to address before go-live:

- Protect or gate public onboarding endpoint.
- Replace `ddl-auto` with versioned schema migrations.
- Externalize all secrets and rotate exposed credentials.
- Add rate limiting and abuse controls.
- Move from in-memory STOMP broker to external broker relay for multi-instance scale.
