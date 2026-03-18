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
- Maven 3.9+
- MySQL 8+

### 1) Clone and enter project

```bash
git clone <your-repository-url>
cd WorkNest-Service-Springboot
```

### 2) Configure application settings

Update `src/main/resources/application.yml` for your environment:

- Master datasource (`spring.datasource.*`)
- Tenant header (`app.tenant.header`, default `X-Tenant-ID`)
- JWT settings (`app.jwt.*`)
- CORS/WebSocket origins

### 3) Build and run locally

```bash
mvn clean package
mvn spring-boot:run
```

Service URL:

- `http://localhost:8080`

### 4) Run with Docker

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

- Endpoint: `/ws` (SockJS enabled)
- App destination prefix: `/app`
- Broker destinations: `/topic`, `/queue`
- User destination prefix: `/user`

Clients should provide JWT during STOMP connection according to client implementation.

---

## Troubleshooting

### 401 or 403 on tenant APIs

Verify:

- Bearer token is valid and not expired
- User role has required permission
- `X-Tenant-ID` is present and valid

### Swagger UI not loading

Verify application startup and availability of `/v3/api-docs`.

### WebSocket connection failures

Verify configured allowed origins for CORS and WebSockets.

### Docker DB connection issues

Verify compose datasource host, username, and password values.

---

## Testing

Automated tests are currently pending implementation.

Recommended coverage:

- Controller integration tests for auth and tenant header behavior
- Service tests for onboarding and schema provisioning
- Security authorization tests for role-based endpoint access

---

## 📄 License

This project is licensed under the MIT License.
See the LICENSE file for more details.
