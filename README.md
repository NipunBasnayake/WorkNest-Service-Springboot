# WorkNest Service

![Java](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3.5-6DB33F?logo=springboot&logoColor=white)
![License](https://img.shields.io/badge/License-Proprietary-lightgrey)
![Build Status](https://img.shields.io/badge/Build-Passing-brightgreen)

Enterprise-grade, multi-tenant backend for the WorkNest platform.
Designed for secure platform onboarding, tenant data isolation, HR and project operations, and real-time collaboration.

---

## Overview

WorkNest Service is a Spring Boot backend built on Java 21 with a hybrid multi-tenant architecture:

- One master database for platform-wide entities
- Dedicated tenant databases or schemas for tenant business data

The service includes:

- Platform tenant onboarding and lifecycle management
- JWT-based authentication and RBAC authorization
- HR and project workflows
- Real-time chat and notifications via WebSockets

---

## Technology Stack

| Layer | Technology |
|---|---|
| Runtime | Java 21 |
| Framework | Spring Boot 3.3.5 |
| Security | Spring Security, JWT |
| Data Access | Spring Data JPA, Hibernate |
| Database | MySQL 8 |
| Migrations | Flyway |
| Real-time | Spring WebSocket, STOMP, SockJS |
| API Docs | SpringDoc OpenAPI, Swagger UI |
| Containerization | Docker, Docker Compose |

---

## Architecture and Data Isolation

### Multi-tenant model

| Scope | Storage | Purpose |
|---|---|---|
| Master | `platform_master` | Platform users, refresh tokens, tenant metadata |
| Tenant | `tenant_<tenantKey>` | Employees, teams, projects, tasks, chats, notifications |

### Request routing logic

Tenant requests are routed dynamically using the `X-Tenant-ID` header.

1. Client sends request to tenant endpoint with `X-Tenant-ID: <tenantKey>`.
2. A tenant context resolver extracts the tenant key.
3. The datasource router selects or initializes the matching tenant datasource.
4. The request executes against the tenant-specific schema or database.

If the header is missing or invalid, the request is rejected according to API/security rules.

### Startup bootstrap flow

At application startup:

1. Bootstrap platform admin is created if it does not exist.
2. Active tenant schemas are initialized/migrated.
3. Optional demo tenant data is seeded when enabled.

---

## Security and Access Control

WorkNest Service uses stateless JWT authentication and role-based access control.

### Access model

| Route Scope | Access |
|---|---|
| Platform routes (`/api/platform/**`) | `PLATFORM_ADMIN` |
| Tenant routes (`/api/tenant/**`) | `TENANT_ADMIN`, `ADMIN`, `MANAGER`, `HR`, `EMPLOYEE` |
| Public routes | Login, refresh, onboarding, Swagger docs, WebSocket handshake |

### Core auth flow

1. `POST /api/auth/login`
2. Use `Authorization: Bearer <accessToken>` for protected APIs
3. Use `POST /api/auth/refresh` to rotate/refresh access
4. Use `POST /api/auth/logout` to revoke session

---

## API Modules

| Domain | Base Endpoints | Description |
|---|---|---|
| Authentication | `/api/auth/*` | Login, refresh, logout, current user |
| Platform (Master) | `/api/platform/onboarding/*`, `/api/platform/tenants/*` | Tenant onboarding and tenant lifecycle |
| HR and Organization | `/api/tenant/employees`, `/api/tenant/teams`, `/api/tenant/attendance`, `/api/tenant/leaves` | Workforce and leave workflows |
| Project Management | `/api/tenant/projects`, `/api/tenant/tasks` | Delivery planning and execution |
| Communications | `/api/tenant/chats/*`, `/api/tenant/announcements`, `/api/tenant/notifications` | Team and HR communication |
| Insights and Governance | `/api/tenant/dashboard`, `/api/tenant/analytics`, `/api/tenant/audit-logs` | Reporting and auditability |
| File and Settings | `/api/tenant/attachments`, `/api/tenant/settings` | File storage and tenant config |

---

## API Quick Start

### Login with curl

```bash
curl -X POST "http://localhost:8080/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "platform.admin@worknest.local",
    "password": "ChangeMe123!"
  }'
```

### Call a tenant endpoint with token

```bash
curl -X GET "http://localhost:8080/api/tenant/dashboard" \
  -H "Authorization: Bearer <access-token>" \
  -H "X-Tenant-ID: <tenantKey>"
```

---

## Project Structure

```text
src/main/java/com/worknest
  auth/         # Auth API, DTOs, services
  config/       # Security, CORS, OpenAPI, datasources, bootstrap initializers
  controller/   # Tenant-facing REST controllers
  master/       # Platform (master DB) domain and onboarding
  security/     # JWT filter, handlers, auth support
  tenant/       # Tenant context, datasource routing, tenant domain/services

src/main/resources
  application.yml
  db/migration/tenant/  # Tenant schema migration scripts
```

---

## Getting Started

### Prerequisites

- Java 21
- Spring Boot
- Spring Security + JWT
- Spring Data JPA / Hibernate
- MySQL
- WebSocket/STOMP
- OpenAPI/Swagger
- Docker / Docker Compose

## Core architecture
- `platform_master` database for platform metadata/auth:
  - `platform_tenants`
  - `platform_users`
  - `refresh_tokens`
- tenant business data in separate DBs:
  - `tenant_<tenantKey>`

## Run locally (IDE)
1. Copy `.env.example` to `.env` and set values (or export vars in your shell/IDE run config).
2. Start MySQL.
3. Run Spring Boot app.

Security note: do not commit real credentials. For production, use a secret manager (AWS Secrets Manager, Azure Key Vault, GCP Secret Manager, Vault).
If this repository has ever contained live credentials, rotate them immediately.

## Profiles
- `application.yml`: shared baseline, env-driven settings only
- `application-dev.yml`: local development defaults
- `application-prod.yml`: production-safe overrides (no `ddl-auto=update`, swagger disabled by default)

## Run with Docker
```bash
docker compose up --build
```

Default compose endpoints:

- MySQL: `localhost:3306`
- Backend: `localhost:8081`

---

## Demo Data Seeding

Enable demo seed in `application.yml`:

```yaml
app:
  bootstrap:
    seed-demo-data: true
```

When enabled, startup seeding creates sample users, teams, projects, tasks, chats, and notifications for active tenants.

---

## API Documentation and WebSockets

### OpenAPI

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

### WebSocket (STOMP)

## Auth flow
1. `POST /api/auth/login`
2. Use `Authorization: Bearer <accessToken>`
3. Refresh via `POST /api/auth/refresh`
4. Logout/revoke via `POST /api/auth/logout`

## Demo seed
Set:
- `BOOTSTRAP_SEED_DEMO_DATA=true`

Seed creates:
- bootstrap platform admin
- tenant demo employees/teams/projects/tasks/chats/announcements/notifications for active tenants

## Important environment variables
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `JWT_SECRET`
- `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`
- `TENANT_HEADER`
- `ALLOWED_ORIGINS`, `WS_ALLOWED_ORIGINS`
- `ATTACHMENTS_DIR`, `ATTACHMENT_MAX_FILE_SIZE_BYTES`, `ATTACHMENT_ALLOWED_MIME_TYPES`
- `BOOTSTRAP_PLATFORM_ADMIN_NAME`, `BOOTSTRAP_PLATFORM_ADMIN_EMAIL`, `BOOTSTRAP_PLATFORM_ADMIN_PASSWORD`
- `BOOTSTRAP_SEED_DEMO_DATA`
- `BOOTSTRAP_DEMO_USER_PASSWORD` (required only when demo data seeding is enabled)

## Phase coverage
- Phase 1: multi-tenant infrastructure
- Phase 2: auth + JWT + refresh + revoke
- Phase 3: employee/team/project/task/attendance/leave
- Phase 4: announcements/notifications/attachments/audit/chats/read-receipts
- Phase 5: dashboards/analytics/hardening/pagination/openapi/docker/demo readiness
