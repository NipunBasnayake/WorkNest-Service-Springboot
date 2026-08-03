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
| Database | MySQL 8.x | Master database plus one database per tenant |
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
| Tenant business plane | entityManagerFactory (tenant) | Per-tenant MySQL databases resolved from `platform_tenants.db_url/db_username/db_password` | Employees, teams, attendance, leaves, projects, tasks, chats, notifications, audit logs |

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

### Startup validation

Startup performs production checks without creating or altering business data:

1. `MasterDatabaseStartupValidator` (`@Order(HIGHEST_PRECEDENCE)`) validates master DB connectivity.
2. `StartupSecretsValidator` (`@Order(5)`) enforces stronger checks when the `prod` profile is active.

Onboarding provisioning flow:

- `POST /api/platform/onboarding/tenants` persists the master registration in an isolated master transaction.
- `TenantProvisioningService` synchronously creates the tenant database, applies the tenant-only schema, and mirrors the tenant administrator employee.
- Registration returns only after the tenant status is committed as ACTIVE; provisioning failures mark it INACTIVE and return an error.

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
    StartupSecretsValidator.java
  controller/             # Tenant REST controllers (HR, project, communication, analytics)
  master/
    controller/           # Platform admin/onboarding APIs
    dto/
    entity/               # PlatformTenant, PlatformUser, RefreshToken
    repository/
    service/              # Platform lifecycle and synchronous tenant provisioning
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
  application-dev.yml
  application-prod.yml

docker-compose.yml
docker-compose.dev.yml
Dockerfile
.env.example
pom.xml
```

---

## Getting Started

### Prerequisites

- JDK 21
- Maven 3.9+
- MySQL 8+ for local development
- Docker and Docker Compose (optional)

### Run locally

The default profile is `dev`. It uses MySQL and resolves to this URL when no database host variables are supplied:

```text
jdbc:mysql://localhost:3306/platform_master?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
```

Copy `.env.example` to `.env` and set the local MySQL password, Supabase credentials, and JWT secret. `application-dev.yml` imports this file through Spring Boot Config Data; there is no custom dotenv parser.

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

- `dev` is active by default and is intentionally MySQL-only.
- `application-dev.yml` imports the optional working-directory `.env` file and maps `MASTER_DB_*` values into `spring.datasource.*`.
- `prod` does not load `.env`; Dockploy or the container runtime must inject the variables into the process environment.
- Both profiles use `com.mysql.cj.jdbc.Driver`, `createDatabaseIfNotExist=true`, and Hibernate schema update. Hibernate detects the MySQL dialect from JDBC metadata.

### Docker development

```bash
docker compose -f docker-compose.dev.yml up --build
```

This starts the backend and MySQL on an isolated network. The backend uses the service hostname `database`; it never uses `localhost` for a container-to-container database connection. MySQL is bound to `127.0.0.1:${MYSQL_PORT:-3307}` for development tools.

### Production and Dockploy

1. Enter the `.env.example` variable names with production values in Dockploy's environment UI. For CLI Compose, use an untracked production env file.
2. Replace every placeholder secret and set the real frontend URLs/origins.
3. Deploy `docker-compose.yml` from this directory.
4. Attach the named `worknest-database-data` volume to persistent VPS storage and retain it across deployments.

```bash
docker compose config
docker compose up -d --build
docker compose ps
```

The production Compose stack exposes only the backend port. MySQL stays on the internal `worknest-network`, has a persistent named volume, and must pass its health check before the backend starts. The backend readiness probe is `/actuator/health/readiness`.

For the current Dockploy MySQL service, the equivalent JDBC URL is assembled without embedding credentials:

```text
jdbc:mysql://${MASTER_DB_HOST}:${MASTER_DB_PORT}/${MASTER_DB_NAME}?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
```

When deploying this repository's Compose stack, the backend always uses the internal service name `database`. When deploying only the backend against the existing Dockploy database service, set `MASTER_DB_HOST=worknestsaas-worknestdb-yjwvog`, `MASTER_DB_PORT=3306`, `MASTER_DB_NAME=worknest_db`, and provide the username/password separately.

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

Use [.env.example](./.env.example) as the canonical inventory. Production secrets are never committed and are consumed through these mappings:

| Area | Required variables |
|---|---|
| Runtime | `SPRING_PROFILES_ACTIVE`, `SERVER_PORT` |
| Master DB | `MASTER_DB_HOST`, `MASTER_DB_PORT`, `MASTER_DB_NAME`, `MASTER_DB_USERNAME`, `MASTER_DB_PASSWORD` |
| Tenant pools | The common `TENANT_DB_*` pool/cache controls when tuning is required; tenant pools reuse the master MySQL driver |
| Storage | `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY`, and `SUPABASE_BUCKET` (or the six category-specific bucket overrides) |
| JWT | `JWT_SECRET`, `JWT_ACCESS_EXPIRATION_MS`, `JWT_REFRESH_EXPIRATION_MS` |
| Mail | `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`; set `MAIL_ENABLED=false` when SMTP is intentionally disabled |
| Browser | `PUBLIC_WEB_BASE_URL`, `PASSWORD_RESET_LINK_BASE_URL`, `ALLOWED_ORIGINS`, `WS_ALLOWED_ORIGINS` |

`application.yml` contains only common behavior and operational defaults. Connection coordinates live in `application-dev.yml` and `application-prod.yml`. Both bind to `spring.datasource.*`; Java code never reads `MASTER_DB_*` directly.

All upload paths use `SupabaseStorageProvider`; there is no filesystem storage provider, uploads directory, or local-path setting. A single private Supabase bucket is supported by `SUPABASE_BUCKET`, while category-specific variables remain available for installations that need separate buckets.

Never commit `.env`. Rotate any database, JWT, SMTP, or Supabase secret that has appeared in source history, and use Dockploy's protected environment-variable storage for production.

---

## Phase Coverage

- Phase 1: Multi-tenant infrastructure (master/tenant separation, resolver/provider/context/filter, onboarding provisioning).
- Phase 2: Authentication and authorization (JWT access tokens, refresh token rotation/revocation, tenant-bound auth checks).
- Phase 3: HR and delivery core (employees, teams, attendance, leaves, projects, tasks).
- Phase 4: Communication and governance (announcements, notifications, team/HR chats, read receipts, audit logs, attachments).
- Phase 5: Operational hardening and delivery readiness (dashboard/analytics APIs, actuator integration, OpenAPI, Dockerization, deterministic provisioning workflows).

Current production hardening gaps to address before go-live:

- Protect or gate public onboarding endpoint.
- Replace `ddl-auto` with versioned schema migrations.
- Externalize all secrets and rotate exposed credentials.
- Add rate limiting and abuse controls.
- Move from in-memory STOMP broker to external broker relay for multi-instance scale.
