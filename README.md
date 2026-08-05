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

