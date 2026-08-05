<div align="center">

# 🚀 WorkNest

### Enterprise Multi-Tenant Company Management Platform

<p align="center">
  <strong>A modern SaaS platform built for organizations to manage employees, projects, teams, tasks, HR operations, recruitment, communication, analytics, and collaboration — all within isolated multi-tenant workspaces.</strong>
</p>

<p align="center">

![Java](https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3-green?style=for-the-badge&logo=springboot)
![React](https://img.shields.io/badge/React-19-blue?style=for-the-badge&logo=react)
![TypeScript](https://img.shields.io/badge/TypeScript-5.x-blue?style=for-the-badge&logo=typescript)
![MySQL](https://img.shields.io/badge/MySQL-8.4-blue?style=for-the-badge&logo=mysql)
![Docker](https://img.shields.io/badge/Docker-Ready-blue?style=for-the-badge&logo=docker)
![JWT](https://img.shields.io/badge/JWT-Secure-orange?style=for-the-badge&logo=jsonwebtokens)
![WebSocket](https://img.shields.io/badge/WebSocket-STOMP-green?style=for-the-badge)

</p>

<p align="center">

![GitHub stars](https://img.shields.io/github/stars/YOUR_USERNAME/WorkNest-Service-Springboot?style=social)
![GitHub forks](https://img.shields.io/github/forks/YOUR_USERNAME/WorkNest-Service-Springboot?style=social)
![GitHub issues](https://img.shields.io/github/issues/YOUR_USERNAME/WorkNest-Service-Springboot)
![GitHub license](https://img.shields.io/github/license/YOUR_USERNAME/WorkNest-Service-Springboot)

</p>

---

</div>

# 📖 Overview

WorkNest is a modern **enterprise Software-as-a-Service (SaaS)** platform that enables multiple organizations to operate independently inside a single application while maintaining complete data isolation.

Every registered company receives its own dedicated tenant database, allowing organizations to securely manage:

- Employees
- Departments
- Teams
- Projects
- Tasks
- Recruitment
- Attendance
- Leave Management
- Performance
- Notifications
- Real-time Chat
- Announcements
- Reports
- Analytics

without sharing data with any other organization.

The platform is designed around **multi-tenant architecture**, making it suitable for commercial SaaS deployment where hundreds or even thousands of organizations can be served from a single application.

---

# ✨ Key Features

## 🏢 Enterprise SaaS Platform

- Multi-tenant architecture
- Organization onboarding
- Automatic tenant provisioning
- Dedicated tenant databases
- Secure tenant isolation
- Platform administration

---

## 👥 Employee Management

- Employee Profiles
- Departments
- Designations
- Teams
- Reporting Managers
- Employment History
- Documents
- Employee Search
- Employee Directory

---

## 📁 Project Management

- Project Lifecycle
- Milestones
- Team Assignment
- Progress Tracking
- Deadlines
- Attachments
- Project Statistics

---

## ✅ Task Management

- Kanban Board
- Task Priorities
- Task Status
- Task Comments
- File Attachments
- Due Dates
- Activity Timeline
- Assignment Workflow

---

## 💬 Real-Time Collaboration

- Team Chat
- Project Conversations
- Instant Notifications
- WebSocket Messaging
- STOMP Protocol
- Online Presence

---

## 📢 Organization Communication

- Announcements
- Company Notifications
- Team Notifications
- Read Receipts
- Broadcast Messages

---

## 📅 Attendance

- Daily Attendance
- Check In
- Check Out
- Attendance Reports
- Attendance Analytics

---

## 🏖 Leave Management

- Leave Requests
- Leave Approval Workflow
- Leave Types
- Leave Balance
- Leave History

---

## 🎯 Recruitment

- Job Vacancies
- Applicant Tracking
- Interview Scheduling
- Candidate Pipeline
- Recruitment Dashboard

---

## 📊 Analytics

- Organization Dashboard
- Employee Statistics
- Project Analytics
- Attendance Analytics
- Recruitment Analytics
- Performance Metrics
- Reports

---

## 🔐 Authentication & Security

- JWT Authentication
- Refresh Tokens
- Role Based Access Control (RBAC)
- Secure Password Hashing
- Protected REST APIs
- Tenant Isolation
- CORS Protection
- CSRF Protection
- Spring Security

---

# 🏗 Architecture

```
                    Internet
                        │
                        │
                Reverse Proxy
             (Traefik / Nginx)
                        │
        ┌───────────────┴───────────────┐
        │                               │
        │                               │
 Frontend (React)                 Spring Boot API
        │                               │
        │                     Authentication
        │                     Authorization
        │                     Tenant Resolver
        │                     Business Logic
        │                               │
        └───────────────┬───────────────┘
                        │
                Platform Database
              (platform_master)
                        │
        ┌───────────────┼───────────────┐
        │               │               │
        │               │               │
   tenant_alpha     tenant_beta     tenant_gamma
      MySQL            MySQL            MySQL
```

---

# 🌍 Multi-Tenant Architecture

Unlike traditional applications where every customer shares the same tables, WorkNest provides complete tenant isolation.

```
Platform
│
├── platform_master
│      │
│      ├── Company A
│      ├── Company B
│      ├── Company C
│      └── ...
│
├── tenant_company_a
│      ├── employees
│      ├── projects
│      ├── tasks
│      ├── chats
│      └── ...
│
├── tenant_company_b
│      ├── employees
│      ├── projects
│      ├── tasks
│      └── ...
│
└── tenant_company_c
```

Each tenant receives:

- Dedicated database
- Dedicated datasource
- Independent connection pool
- Complete data isolation
- Independent transactions
- Independent caching

---

# ⚡ Technology Stack

## Backend

| Technology | Version |
|------------|----------|
| Java | 21 |
| Spring Boot | 3.3 |
| Spring Security | 6 |
| Spring Data JPA | Latest |
| Hibernate | 6 |
| MySQL | 8 |
| JWT | 0.11 |
| STOMP | Latest |
| WebSocket | Spring |
| Maven | Latest |

---

## Frontend

| Technology | Version |
|------------|----------|
| React | 19 |
| TypeScript | 5 |
| Vite | Latest |
| Tailwind CSS | Latest |
| React Router | Latest |
| Axios | Latest |

---

## Infrastructure

- Docker
- Docker Compose
- Dokploy
- Traefik
- GitHub Actions (planned)
- Supabase Storage
- SMTP
- Let's Encrypt

---

# 📂 Project Structure

```
WorkNest
│
├── WorkNest-Service-Springboot
│
├── src
│   ├── auth
│   ├── config
│   ├── security
│   ├── master
│   ├── tenant
│   ├── websocket
│   ├── notification
│   ├── recruitment
│   ├── employee
│   ├── project
│   ├── task
│   ├── attendance
│   ├── leave
│   ├── analytics
│   ├── reports
│   ├── storage
│   └── common
│
├── docker
├── docs
├── scripts
├── pom.xml
└── README.md
```

---

# 🎯 Design Principles

WorkNest follows enterprise software engineering principles:

- Clean Architecture
- Layered Architecture
- Domain Driven Design
- SOLID Principles
- Repository Pattern
- DTO Pattern
- Dependency Injection
- Secure by Default
- Stateless REST APIs
- Production Ready Logging
- Container Ready
- Cloud Native Design

---

# 🚀 Why WorkNest?

Unlike many internal HR systems, WorkNest is designed as a true SaaS platform.

✔ Multi-Tenant

✔ Enterprise Security

✔ Horizontal Scalability

✔ Cloud Ready

✔ Container Ready

✔ Production Ready

✔ Docker Native

✔ Modern REST APIs

✔ Real-Time Communication

✔ Modular Architecture

✔ Role Based Access Control

✔ Automatic Tenant Provisioning

✔ Enterprise Authentication

---

# 📸 Screenshots

> Screenshots will be added soon.

```
Dashboard

[ Screenshot Here ]

------------------------------------------------

Projects

[ Screenshot Here ]

------------------------------------------------

Kanban Board

[ Screenshot Here ]

------------------------------------------------

Recruitment

[ Screenshot Here ]

------------------------------------------------

Analytics

[ Screenshot Here ]
```

---

# 📚 Documentation

The project documentation is organized under the `/docs` directory.

| Document | Description |
|----------|-------------|
| Architecture | System Architecture |
| Deployment | Production Deployment Guide |
| Docker | Docker Setup |
| API | REST API |
| Security | Security Guide |
| Multi-Tenant | Tenant Architecture |
| Database | Database Design |

---

# 🧩 Backend Modules

The WorkNest backend is organized into modular business domains, enabling independent development, scalability, and maintainability.

---

## 🔐 Authentication Module

Responsible for authentication and identity management.

### Features

- User Login
- User Logout
- JWT Access Token
- Refresh Token
- Password Reset
- Email Verification
- Secure Password Hashing
- Token Rotation
- Session Validation

---

## 👤 Platform Administration

The platform administration module manages the SaaS platform itself.

### Responsibilities

- Platform Admin Management
- Tenant Registration
- Tenant Approval
- Tenant Provisioning
- Tenant Database Creation
- Platform Configuration
- Platform Analytics

---

## 🏢 Tenant Management

Each organization is represented as an isolated tenant.

Responsibilities include:

- Tenant Creation
- Tenant Configuration
- Database Provisioning
- Connection Pool Management
- Tenant Isolation
- Tenant Lifecycle

---

## 👥 Employee Module

Provides complete employee lifecycle management.

### Features

- Employee Registration
- Employee Profile
- Department Assignment
- Team Assignment
- Designation Management
- Employment Status
- Profile Image
- Employee Documents
- Search & Filtering

---

## 👨‍💼 Department Module

- Department Creation
- Department Updates
- Employee Assignment
- Department Statistics

---

## 👨‍👩‍👧‍👦 Team Module

- Team Creation
- Team Leaders
- Team Members
- Team Projects
- Team Chat

---

## 📁 Project Module

Project lifecycle management.

### Features

- Create Project
- Update Project
- Archive Project
- Project Status
- Milestones
- Members
- Progress Tracking

---

## ✅ Task Module

Powerful Kanban-based task management.

### Features

- Task Creation
- Assignment
- Priorities
- Due Dates
- Labels
- Comments
- Attachments
- Activity Timeline
- Status Updates

Task States

```
TODO

↓

IN PROGRESS

↓

REVIEW

↓

DONE
```

---

## 💬 Chat Module

Real-time communication powered by WebSockets.

Supports:

- Team Chat
- Project Chat
- Direct Messaging
- Read Status
- Message History
- Notifications

---

## 🔔 Notification Module

Event-driven notification system.

Examples:

- New Task
- Task Assigned
- Leave Approved
- Interview Scheduled
- Employee Joined
- Announcement Published

---

## 📢 Announcement Module

Broadcast organization-wide announcements.

Supports:

- Rich Text
- Priority Levels
- Attachments
- Audience Targeting

---

## 📅 Attendance Module

Employee attendance management.

Features:

- Check In
- Check Out
- Attendance History
- Reports
- Analytics

---

## 🌴 Leave Module

Leave management workflow.

Features

- Leave Requests
- Leave Types
- Approvals
- Rejections
- Leave Balance
- History

---

## 🎯 Recruitment Module

Applicant Tracking System (ATS)

Features

- Job Positions
- Candidates
- Interview Scheduling
- Interview Feedback
- Hiring Pipeline
- Candidate Documents

---

## 📈 Reports Module

Generates organization reports.

Includes

- Employee Reports
- Attendance Reports
- Leave Reports
- Recruitment Reports
- Project Reports

---

## 📊 Analytics Module

Business intelligence dashboard.

Provides

- KPIs
- Employee Analytics
- Productivity
- Recruitment Insights
- Attendance Statistics

---

## ☁ Storage Module

File management using Supabase Storage.

Supports

- Images
- Documents
- Attachments
- Reports

---

# 🔒 Security Architecture

Security is implemented across multiple layers.

```
                Request

                   │

                   ▼

          Spring Security Filter

                   │

                   ▼

            CORS Validation

                   │

                   ▼

             JWT Validation

                   │

                   ▼

         Tenant Resolution Filter

                   │

                   ▼

          Authorization Check

                   │

                   ▼

            Controller Layer

                   │

                   ▼

             Business Logic

                   │

                   ▼

              Database
```

---

## Authentication Flow

```
User

 │

 │ Login

 ▼

Spring Security

 │

 ▼

Authentication Manager

 │

 ▼

UserDetailsService

 │

 ▼

Database

 │

 ▼

JWT Generation

 │

 ▼

Access Token

Refresh Token

 │

 ▼

Client
```

---

## Authorization Model

WorkNest uses Role-Based Access Control (RBAC).

### Platform Roles

```
PLATFORM_ADMIN
```

---

### Tenant Roles

```
TENANT_ADMIN

ADMIN

HR

MANAGER

TEAM_LEAD

EMPLOYEE
```

Each API endpoint is protected based on user roles.

---

# 🏢 Multi-Tenant Request Lifecycle

Every request passes through tenant resolution before accessing business data.

```
Incoming Request

        │

        ▼

Extract Tenant Header

        │

        ▼

Validate JWT

        │

        ▼

Load Tenant

(platform_master)

        │

        ▼

Resolve DataSource

        │

        ▼

Tenant Database

        │

        ▼

Execute Business Logic
```

---

# 🗄 Database Architecture

```
                     MySQL Server

                           │

      ┌────────────────────┼────────────────────┐

      │                    │                    │

platform_master     tenant_companyA     tenant_companyB

      │                    │                    │

      │                    │                    │

 Platform Tables      Business Data      Business Data
```

---

## Master Database

Stores platform-wide information.

Typical entities:

- Platform Users
- Platform Roles
- Tenants
- Refresh Tokens
- Tenant Metadata

---

## Tenant Database

Each tenant contains its own business data.

Typical entities:

- Employees
- Departments
- Teams
- Projects
- Tasks
- Chat
- Notifications
- Leave
- Attendance
- Recruitment

---

# 🔄 Tenant Provisioning Workflow

```
Register Company

      │

      ▼

Validate Input

      │

      ▼

Create Tenant Record

(platform_master)

      │

      ▼

Create Database

tenant_company

      │

      ▼

Initialize Schema

      │

      ▼

Seed Default Data

      │

      ▼

Create Admin User

      │

      ▼

Tenant Ready
```

---

# 📦 Package Structure

```
src/

├── auth/
├── common/
├── config/
├── exception/
├── master/
│   ├── controller/
│   ├── entity/
│   ├── repository/
│   └── service/
│
├── notification/
├── security/
│   ├── filter/
│   ├── jwt/
│   └── config/
│
├── storage/
├── tenant/
│   ├── attendance/
│   ├── employee/
│   ├── leave/
│   ├── project/
│   ├── recruitment/
│   ├── reports/
│   ├── task/
│   └── analytics/
│
├── websocket/
└── Main.java
```

---

# 🔄 Request Lifecycle

```
Browser

↓

Traefik

↓

Spring Boot

↓

Security Filter

↓

JWT Filter

↓

Tenant Filter

↓

Controller

↓

Service

↓

Repository

↓

JPA

↓

MySQL
```

---

# 📡 WebSocket Architecture

```
Client

 │

 ▼

WebSocket

 │

 ▼

STOMP Endpoint

 │

 ▼

Message Broker

 │

 ▼

Topic

 │

 ▼

Subscribed Clients
```

Supported capabilities:

- Live Chat
- Notifications
- Team Messaging
- Project Discussions

---

# ✉ Email System

Supports SMTP integration for:

- Welcome Emails
- Password Reset
- Interview Invitations
- Leave Notifications
- System Alerts

---

# 📁 File Storage

The application uses **Supabase Storage**.

Supported file types:

- Images
- PDFs
- Office Documents
- ZIP Files

Maximum upload size is configurable.

---

# 📈 Scalability

Designed for cloud-native deployment.

Current architecture supports:

- Multiple Organizations
- Multiple Databases
- Docker
- Dokploy
- Reverse Proxies
- HTTPS
- Cloud Storage
- Horizontal Frontend Scaling

---

# 🚀 Getting Started

This guide walks you through setting up the WorkNest backend for local development and production deployment.

---

# 📋 Prerequisites

Ensure the following software is installed before starting.

| Software | Version |
|-----------|----------|
| Java | 21+ |
| Maven | 3.9+ |
| MySQL | 8.0+ |
| Git | Latest |
| Docker | Latest *(optional)* |
| Docker Compose | Latest *(optional)* |
| IntelliJ IDEA | Recommended |

---

# 💻 Clone Repository

```bash
git clone https://github.com/YOUR_USERNAME/WorkNest-Service-Springboot.git

cd WorkNest-Service-Springboot
```

---

# ⚙ Environment Configuration

Create a local environment file.

```bash
cp .env.example .env
```

Configure the values according to your environment.

Example:

```dotenv
#############################################
# APPLICATION
#############################################

SPRING_PROFILES_ACTIVE=dev

#############################################
# DATABASE
#############################################

MASTER_DB_HOST=localhost
MASTER_DB_PORT=3306
MASTER_DB_NAME=platform_master
MASTER_DB_USERNAME=root
MASTER_DB_PASSWORD=yourpassword

#############################################
# JWT
#############################################

JWT_SECRET=YOUR_BASE64_SECRET

JWT_ACCESS_EXPIRATION_MS=900000
JWT_REFRESH_EXPIRATION_MS=604800000

#############################################
# SUPABASE
#############################################

SUPABASE_URL=https://your-project.supabase.co

SUPABASE_SERVICE_ROLE_KEY=your-service-role-key

SUPABASE_BUCKET=worknest

#############################################
# EMAIL
#############################################

MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=example@gmail.com
MAIL_PASSWORD=yourpassword

#############################################
# APPLICATION URLS
#############################################

PUBLIC_WEB_BASE_URL=http://localhost:5173

PASSWORD_RESET_LINK_BASE_URL=http://localhost:5173/reset-password

#############################################
# CORS
#############################################

ALLOWED_ORIGINS=http://localhost:5173

WS_ALLOWED_ORIGINS=http://localhost:5173
```

---

# 🗄 MySQL Setup

Start MySQL.

Create the master database.

```sql
CREATE DATABASE platform_master;
```

No tenant databases need to be created manually.

WorkNest automatically provisions new tenant databases during organization registration.

---

# 🧹 Clean Project

```bash
mvn clean
```

---

# 🔨 Compile

```bash
mvn compile
```

---

# ▶ Run Application

```bash
mvn spring-boot:run
```

Expected output:

```
Started Main
```

Backend API:

```
http://localhost:8080
```

Swagger (Development)

```
http://localhost:8080/swagger-ui/index.html
```

Actuator

```
http://localhost:8080/actuator/health
```

---

# 🖥 Running with IntelliJ IDEA

Open

```
File
    Open Project
```

Select

```
WorkNest-Service-Springboot
```

Wait until Maven imports all dependencies.

Run

```
Main.java
```

or

```
Spring Boot Configuration
```

---

# 💙 Running with VS Code

Install extensions

- Java Extension Pack
- Spring Boot Extension Pack
- Maven for Java

Run

```
Main.java
```

or

```
mvn spring-boot:run
```

---

# 🐳 Docker

Build image

```bash
docker build -t worknest-backend .
```

Run container

```bash
docker run \
-p 8080:8080 \
--env-file .env \
worknest-backend
```

---

# 🐳 Docker Compose

Start

```bash
docker compose up -d
```

Stop

```bash
docker compose down
```

View logs

```bash
docker compose logs -f
```

Restart

```bash
docker compose restart
```

---

# 📂 Important Configuration Files

```
application.yml

application-dev.yml

application-prod.yml

.env.example

Dockerfile

docker-compose.yml
```

---

# 🌐 Profiles

## Development

```
dev
```

Uses

- Local MySQL
- Local .env
- Development Logging

Run

```bash
mvn spring-boot:run
```

---

## Production

```
prod
```

Uses

- Environment Variables
- External MySQL
- Optimized Logging

Run

```bash
SPRING_PROFILES_ACTIVE=prod
```

---

# 📦 Building Production JAR

```bash
mvn clean package
```

Generated

```
target/

worknest-service.jar
```

Run

```bash
java -jar worknest-service.jar
```

---

# 🔑 Environment Variables

## Required

| Variable | Required |
|-----------|----------|
| MASTER_DB_HOST | ✅ |
| MASTER_DB_PORT | ✅ |
| MASTER_DB_NAME | ✅ |
| MASTER_DB_USERNAME | ✅ |
| MASTER_DB_PASSWORD | ✅ |
| JWT_SECRET | ✅ |
| SUPABASE_URL | ✅ |
| SUPABASE_SERVICE_ROLE_KEY | ✅ |
| MAIL_HOST | ✅ |
| MAIL_USERNAME | ✅ |
| MAIL_PASSWORD | ✅ |

---

# 🔒 JWT Secret

Generate a secure Base64 secret.

Linux

```bash
openssl rand -base64 64
```

PowerShell

```powershell
[Convert]::ToBase64String((1..64 | ForEach-Object {Get-Random -Maximum 256}))
```

---

# 📡 Health Endpoints

Liveness

```
GET

/actuator/health/liveness
```

Readiness

```
GET

/actuator/health/readiness
```

General Health

```
GET

/actuator/health
```

---

# 📚 API Documentation

Swagger UI

```
/swagger-ui/index.html
```

OpenAPI

```
/v3/api-docs
```

---

# 📨 Default Platform Administrator

When bootstrap is enabled

```
Email

admin@worknest.com
```

Password

```
Configured through environment variables
```

Disable bootstrap after the first deployment.

---

# 🏢 Automatic Tenant Provisioning

When a new company registers:

```
Company Registration

↓

Validate Request

↓

Create Platform Record

↓

Create MySQL Database

↓

Create Tables

↓

Seed Initial Data

↓

Create Administrator

↓

Ready
```

No manual SQL scripts are required for tenant creation.

---

# 🔍 Verify Installation

Open

```
http://localhost:8080/actuator/health
```

Expected response

```json
{
  "status": "UP"
}
```

---

# 🧪 Running Tests

Run all tests

```bash
mvn test
```

Run integration tests

```bash
mvn verify
```

---

# 📝 Development Workflow

```text
Clone Repository

↓

Configure .env

↓

Create platform_master

↓

mvn clean

↓

mvn compile

↓

mvn spring-boot:run

↓

Develop

↓

Test

↓

Commit

↓

Push

↓

Deploy
```

---

