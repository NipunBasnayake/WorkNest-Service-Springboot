# WorkNest Service (Spring Boot)

Multi-tenant backend for the WorkNest Software Company Management Platform.

## Tech stack
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

Backend: `http://localhost:8080`

## API docs
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

## Required tenant header
For tenant APIs, include:
- `X-Tenant-ID: <tenantKey>`

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
