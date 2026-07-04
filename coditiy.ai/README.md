# ⚙️ JOBSEEK — Distributed Job Scheduler Platform

> A production-grade, enterprise-ready distributed background job execution platform built with Spring Boot 3, PostgreSQL, Quartz Scheduler, React + Vite, WebSockets, and Docker Compose.

[![Java](https://img.shields.io/badge/Java-17-orange?style=flat-square&logo=openjdk)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen?style=flat-square&logo=springboot)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18-blue?style=flat-square&logo=react)](https://reactjs.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue?style=flat-square&logo=postgresql)](https://www.postgresql.org/)
[![Quartz](https://img.shields.io/badge/Quartz-2.3.x-orange?style=flat-square)](http://www.quartz-scheduler.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square&logo=docker)](https://docs.docker.com/compose/)

---

## 📋 Project Overview

JOBSEEK is a self-hosted distributed background job scheduling platform designed for engineering teams that need reliable, observable, and scalable background task execution.

The platform supports enqueueing and executing jobs across a fleet of worker nodes, scheduling recurring tasks via Quartz, retrying failed jobs with configurable backoff strategies, and monitoring the entire cluster in real time through a WebSocket-powered dashboard — all without any third-party message broker.

---

## ✨ Key Features

| Feature | Description |
|---------|-------------|
| **Multi-type Jobs** | Immediate, Delayed, Scheduled, Recurring (Quartz), Batch |
| **Distributed Execution** | Atomic `SELECT FOR UPDATE SKIP LOCKED` — no duplicate processing |
| **Quartz Scheduling** | CRON, Fixed Interval, One-Time, Delayed triggers backed by JDBCJobStore |
| **Retry Policies** | Fixed, Linear, and Exponential backoff with configurable caps |
| **Dead Letter Queue** | Jobs exceeding max attempts are automatically quarantined |
| **Worker Heartbeats** | Each worker sends a heartbeat every 10 s |
| **Dead Worker Reaper** | Leader-elected background process recovers jobs from dead workers |
| **Real-time Dashboard** | WebSocket (STOMP + SockJS) live updates — no page refresh |
| **Multi-tenancy** | Organizations → Projects → Queues → Jobs hierarchy |
| **JWT Authentication** | Stateless Bearer token authentication |
| **Concurrency Control** | Per-queue thread pool with configurable concurrency limits |
| **Job Logs** | Structured execution log per job, viewable in the dashboard |
| **Graceful Shutdown** | Workers drain in-flight jobs before terminating |

---

## 🛠️ Technology Stack

| Layer | Technology | Purpose |
|-------|-----------|---------|
| **Backend Framework** | Spring Boot 3 | REST API, DI, lifecycle |
| **Security** | Spring Security + JWT | Authentication, authorization |
| **ORM** | Spring Data JPA / Hibernate | Database access |
| **Scheduler** | Quartz 2.3.x (JDBCJobStore) | Persistent cron/interval scheduling |
| **Database** | PostgreSQL 15 | Persistent storage, SKIP LOCKED |
| **Migrations** | Flyway | Schema versioning |
| **Real-time** | WebSocket (STOMP + SockJS) | Live push updates |
| **Frontend** | React 18 + Vite + TypeScript | Single-page dashboard |
| **Charts** | Recharts | Throughput area chart |
| **Containerization** | Docker + Docker Compose | Deployment orchestration |

---

## 🏗️ System Architecture Overview

```
┌────────────────────────────────────────────────────────────────────────┐
│                           JOBSEEK Platform                             │
│                                                                        │
│   ┌──────────────────┐    REST / WebSocket    ┌─────────────────────┐ │
│   │  React Dashboard │◄──────────────────────►│  Spring Boot API    │ │
│   │  (Port 3001)     │                        │  (Port 8080)        │ │
│   └──────────────────┘                        └────────┬────────────┘ │
│                                                        │ JDBC / JPA   │
│                                               ┌────────▼────────────┐ │
│                                               │   PostgreSQL 15     │ │
│                                               │   (Port 5432)       │ │
│                                               └────────┬────────────┘ │
│                                                        │ SKIP LOCKED  │
│                          ┌─────────────────────────────┤              │
│                          │                             │              │
│                ┌─────────▼──────────┐       ┌─────────▼──────────┐  │
│                │  Worker Node 1     │       │  Worker Node 2     │  │
│                │  · QueuePoller     │       │  · QueuePoller     │  │
│                │  · JobExecutor     │       │  · JobExecutor     │  │
│                │  · Heartbeat       │       │  · Heartbeat       │  │
│                │  · Reaper (leader) │       │  · Reaper (standby)│  │
│                └────────────────────┘       └────────────────────┘  │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 📁 Project Folder Structure

```
jobseek/
├── src/main/java/ai/coditiy/scheduler/
│   ├── config/                    # Security, WebSocket, CORS, Quartz config
│   ├── controller/                # REST controllers
│   │   ├── AuthController.java    # /api/auth — register & login
│   │   ├── JobController.java     # /api/jobs — job CRUD
│   │   ├── WorkspaceController.java  # Orgs, Projects, Queues, Retry Policies, ScheduledJobs
│   │   ├── ScheduleController.java   # /api/schedules — Quartz schedule management
│   │   ├── DashboardController.java  # /api/dashboard/stats
│   │   └── HealthController.java     # /api/health
│   ├── dto/                       # Request/Response data transfer objects
│   ├── exception/                 # GlobalExceptionHandler
│   ├── filter/                    # RequestIdFilter
│   ├── model/                     # JPA entities (Job, Queue, Worker, etc.)
│   ├── repository/                # Spring Data JPA repositories
│   └── service/
│       ├── auth/                  # UserDetailsService, JwtTokenProvider
│       ├── job/                   # QuartzJobLauncher, ScheduledJobPromoter
│       ├── schedule/              # ScheduleService (Quartz integration)
│       ├── worker/                # QueuePollerManager, JobClaimService,
│       │                          # JobExecutorService, WorkerHeartbeatService,
│       │                          # ReaperService, RetryBackoffCalculator, LockService
│       └── ws/                    # WebSocket broadcast services
├── src/main/resources/
│   ├── application.yml            # Application configuration
│   └── db/migration/              # Flyway SQL migrations
│       ├── V1__Initial_Schema.sql # Core tables
│       ├── V2__Quartz_Schema.sql  # Quartz JDBCJobStore tables
│       └── V3__Schedule_Tables.sql # Extended schedule tables
├── dashboard/                     # React + Vite frontend
│   ├── src/
│   │   ├── components/            # Login, LogsModal, Toast, ConfirmDialog
│   │   ├── hooks/                 # useToast
│   │   ├── services/              # api.ts, websocket.ts
│   │   ├── App.tsx                # Main application component
│   │   └── index.css              # Global dark-theme styles
│   ├── Dockerfile                 # Nginx-based production build
│   └── package.json
├── Dockerfile                     # Multi-stage backend build
├── docker-compose.yml             # Full stack orchestration (5 services)
├── .dockerignore                  # Build context exclusions
└── README.md
```

---

## 🚀 Installation and Setup

### Prerequisites

| Requirement | Version |
|-------------|---------|
| Docker Desktop | 24+ |
| Docker Compose | v2+ |
| Java (local dev only) | 17+ |
| Node.js (local dev only) | 18+ |

### Clone the repository

```bash
git clone https://github.com/your-org/jobseek.git
cd jobseek
```

---

## ⚙️ Environment Variables

Create a `.env` file in the project root to override any defaults:

```env
# Database
POSTGRES_DB=scheduler
POSTGRES_USER=postgres
POSTGRES_PASSWORD=your-secure-password

# JWT — MUST be changed in production (minimum 256-bit key)
JWT_SECRET=your-super-secret-256-bit-key-for-production

# Worker IDs (auto-generated by default in docker-compose)
SCHEDULER_WORKER_ID=worker-node-1
```

All variables have safe development defaults and will work out of the box without a `.env` file.

| Variable | Default | Description |
|----------|---------|-------------|
| `POSTGRES_DB` | `scheduler` | PostgreSQL database name |
| `POSTGRES_USER` | `postgres` | PostgreSQL username |
| `POSTGRES_PASSWORD` | `postgrespassword` | PostgreSQL password |
| `JWT_SECRET` | *(dev key)* | JWT signing key — **change in production** |
| `SCHEDULER_WORKER_ID` | `worker-node-{uuid}` | Unique worker node identifier |
| `SPRING_PROFILES_ACTIVE` | `api,worker` | Spring profiles to activate |

---

## 🐳 Docker Setup and Execution

### Start everything

```bash
docker compose up --build
```

This will:
1. Build the Spring Boot backend image (multi-stage Maven build)
2. Build the React dashboard image (Vite build → Nginx)
3. Start PostgreSQL 15 and run Flyway migrations automatically
4. Start the **API server** on port `8080`
5. Start **two worker nodes** that compete for jobs using SKIP LOCKED
6. Start the **React dashboard** on port `3001`

### Stop everything

```bash
docker compose down
```

### Stop and wipe all data

```bash
docker compose down -v
```

### View service logs

```bash
docker compose logs -f scheduler-api
docker compose logs -f scheduler-worker-1
docker compose logs -f scheduler-worker-2
```

### Check status

```bash
docker compose ps
```

### Access Points

| Service | URL |
|---------|-----|
| Dashboard | http://localhost:3001 |
| Spring Boot API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Health Check | http://localhost:8080/api/health |
| PostgreSQL | localhost:5432 (user: postgres) |

---

## 💻 Running Locally (Without Docker)

### Backend

```bash
# Requires a running PostgreSQL instance on port 5432
cd jobseek
./mvnw spring-boot:run
```

The default `application.yml` activates both the `api` and `worker` profiles, running everything in a single JVM.

### Frontend

```bash
cd dashboard
npm install
npm run dev
```

The Vite dev server starts at http://localhost:5173 with hot-reload. The API proxy is configured in `vite.config.ts` to forward `/api` requests to `http://localhost:8080`.

---

## 📦 Build and Deployment

### Build backend JAR

```bash
./mvnw clean package -DskipTests
```

### Build and push Docker images

```bash
docker build -t jobseek-api:latest .
docker build -t jobseek-dashboard:latest ./dashboard
```

### Production deployment

For production, override credentials via `.env` or environment variables injected by your orchestrator. See [DEPLOYMENT.md](./DEPLOYMENT.md) for full instructions.

---

## 📖 API Overview

All endpoints (except `/api/auth/**` and `/api/health`) require a valid JWT Bearer token:

```
Authorization: Bearer <token>
```

### Endpoint Groups

| Group | Base Path | Description |
|-------|-----------|-------------|
| Authentication | `/api/auth` | Register and login |
| Organizations | `/api/organizations` | Org CRUD |
| Projects | `/api/organizations/{orgId}/projects` | Project CRUD |
| Queues | `/api/projects/{projectId}/queues` | Queue management |
| Queue Actions | `/api/queues/{id}/pause`, `/resume` | Pause/resume |
| Retry Policies | `/api/retry-policies` | Policy CRUD |
| Jobs | `/api/jobs` | Job dispatch, list, cancel, retry |
| Job Logs | `/api/jobs/{id}/logs` | Execution log retrieval |
| Schedules (Quartz) | `/api/projects/{id}/schedules` | Quartz schedule management |
| Dashboard | `/api/dashboard/stats` | Aggregate metrics |
| Workers | `/api/workers` | Worker node list |
| Health | `/api/health` | System health check |

See [API_DOCUMENTATION.md](./API_DOCUMENTATION.md) for complete endpoint reference.

---

## 🔐 Authentication

JOBSEEK uses **JWT (JSON Web Tokens)** for stateless authentication.

1. Register: `POST /api/auth/register` — creates a user, default org, and default project
2. Login: `POST /api/auth/login` — returns a JWT valid for 24 hours
3. Use the token as `Authorization: Bearer <token>` on all subsequent requests

---

## ⚡ Job Lifecycle

```
QUEUED → CLAIMED → RUNNING → COMPLETED
                            → FAILED → RETRYING (re-QUEUED)
                                     → DEAD_LETTER (max attempts exceeded)
SCHEDULED → QUEUED (when scheduledAt is reached)
```

---

## 📥 Queue Lifecycle

```
CREATED → ACTIVE (polling, dispatching)
        → PAUSED (stops accepting new dispatch)
        → RESUMED → ACTIVE
```

---

## 💻 Worker Lifecycle

```
STARTUP → ACTIVE (heartbeating, polling)
        → OFFLINE (graceful shutdown)
        → DEAD (missed heartbeat — detected by Reaper)
```

---

## 🔄 Retry Mechanism

When a job fails, the platform calculates the next attempt time using the queue's attached retry policy:

| Strategy | Formula |
|----------|---------|
| **FIXED** | `retry_at = now + base_delay_seconds` |
| **LINEAR** | `retry_at = now + base_delay + (attempt - 1) × base_delay` |
| **EXPONENTIAL** | `retry_at = now + min(base_delay × 2^(attempt-1), max_delay)` |

If no retry policy is attached, a default 10-second fixed delay is used. Once `attempt_count >= max_attempts`, the job moves to the Dead Letter Queue.

---

## ☠️ Dead Letter Queue

Jobs that have exhausted all retry attempts are moved to `DEAD_LETTER` status and an entry is created in the `dead_letter_queue` table. They can be manually re-queued from the dashboard using the **Retry** button.

---

## 📸 Screenshots

> Start the application and visit http://localhost:3001 to see the live dashboard.

- **Dashboard Overview** — Real-time stats, throughput chart, queue overview cards
- **Active Job Queues** — Job explorer with filtering, pagination, and inline log viewer
- **Quartz Schedules** — Register and manage CRON / fixed-interval / one-time schedules
- **Cluster Workers** — Live worker nodes with heartbeat status and stale detection
- **Retry Policies** — Create and view backoff policies
- **Live Event Feed** — Real-time WebSocket event stream

---

## 🧪 Testing Instructions

See [TESTING.md](./TESTING.md) for detailed testing strategy.

### Quick smoke test

```bash
# 1. Start the stack
docker compose up --build -d

# 2. Verify health
curl http://localhost:8080/api/health

# 3. Register and login
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"Test1234!","email":"test@example.com"}'

TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"Test1234!"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

# 4. List organizations
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/organizations
```

---

## ⚡ Performance Considerations

- **`SELECT FOR UPDATE SKIP LOCKED`** eliminates contention between workers — no polling overhead from lock contention
- **Per-queue `ThreadPoolExecutor`** bounds concurrent execution to `concurrencyLimit` threads
- **Composite index** on `(queue_id, status, scheduled_at)` makes polling O(log N)
- **JDBCJobStore** for Quartz enables clustered, persistent scheduling without duplicate triggers
- Workers sleep for `poll-interval-ms` (default: 1000 ms) when no jobs are available

---

## 🔮 Future Improvements

- [ ] OAuth2 / SSO authentication
- [ ] Role-based access control (RBAC) at the queue level
- [ ] Prometheus metrics + Grafana integration
- [ ] Email / Slack alerting on job failures
- [ ] Dead Letter Queue replay bulk UI
- [ ] Job dependency graph (run B after A completes)
- [ ] Multi-region worker support
- [ ] Rate limiting per queue
- [ ] CPU / memory metrics on Worker Nodes page (JVM MBeans)

---

## 📄 License

This project is released for educational and evaluation purposes.

---

*JOBSEEK — Enterprise job scheduling, simplified.*
