# ARCHITECTURE.md — System Architecture Reference

---

## 1. High-Level System Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              JOBSEEK Platform                               │
│                                                                             │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │                         Browser Client                               │  │
│  │              React 18 + Vite SPA (http://localhost:3001)             │  │
│  └─────────────────────────┬────────────────────────────────────────────┘  │
│                             │ REST (JWT)  │ WebSocket (STOMP/SockJS)        │
│  ┌─────────────────────────▼────────────────────────────────────────────┐  │
│  │                    Spring Boot API Server                             │  │
│  │  ┌────────────┐  ┌──────────────┐  ┌────────────┐  ┌─────────────┐ │  │
│  │  │AuthController│ │JobController │  │WorkspacCtrl│  │ScheduleCtrl │ │  │
│  │  └────────────┘  └──────────────┘  └────────────┘  └─────────────┘ │  │
│  │  ┌──────────────────────────────────────────────────────────────┐   │  │
│  │  │             Spring Security (JWT Filter Chain)               │   │  │
│  │  └──────────────────────────────────────────────────────────────┘   │  │
│  │  ┌──────────────────────────────────────────────────────────────┐   │  │
│  │  │         Quartz Scheduler (JDBCJobStore, Clustered)           │   │  │
│  │  └──────────────────────────────────────────────────────────────┘   │  │
│  │  ┌──────────────────────────────────────────────────────────────┐   │  │
│  │  │        WebSocket Broker (STOMP — in-memory broker)           │   │  │
│  │  └──────────────────────────────────────────────────────────────┘   │  │
│  └──────────────────────────┬───────────────────────────────────────────┘  │
│                             │ JDBC / JPA                                    │
│  ┌──────────────────────────▼───────────────────────────────────────────┐  │
│  │                      PostgreSQL 15                                    │  │
│  │   jobs · queues · workers · job_logs · job_executions                │  │
│  │   dead_letter_queue · scheduled_jobs · locks · quartz_tables         │  │
│  └──────────────────────────┬───────────────────────────────────────────┘  │
│                             │ SELECT FOR UPDATE SKIP LOCKED                 │
│       ┌─────────────────────┴──────────────────────┐                       │
│       │                                            │                       │
│  ┌────▼────────────────────────────┐  ┌────────────▼──────────────────┐   │
│  │      Worker Node 1              │  │      Worker Node 2             │   │
│  │  ┌─────────────────────────┐   │  │  ┌─────────────────────────┐  │   │
│  │  │ WorkerHeartbeatService  │   │  │  │ WorkerHeartbeatService  │  │   │
│  │  │ (every 10s)             │   │  │  │ (every 10s)             │  │   │
│  │  └─────────────────────────┘   │  │  └─────────────────────────┘  │   │
│  │  ┌─────────────────────────┐   │  │  ┌─────────────────────────┐  │   │
│  │  │  QueuePollerManager     │   │  │  │  QueuePollerManager     │  │   │
│  │  │  ├── QueuePoller(q1)   │   │  │  │  ├── QueuePoller(q1)   │  │   │
│  │  │  └── QueuePoller(q2)   │   │  │  │  └── QueuePoller(q2)   │  │   │
│  │  └─────────────────────────┘   │  │  └─────────────────────────┘  │   │
│  │  ┌─────────────────────────┐   │  │  ┌─────────────────────────┐  │   │
│  │  │ ReaperService (LEADER)  │   │  │  │ ReaperService (STANDBY) │  │   │
│  │  │ (every 30s)             │   │  │  │ (lock not acquired)     │  │   │
│  │  └─────────────────────────┘   │  │  └─────────────────────────┘  │   │
│  └─────────────────────────────────┘  └────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Component Interaction

```mermaid
graph TB
    Browser["🌐 React Dashboard"]
    API["⚙️ Spring Boot API"]
    Quartz["⏱️ Quartz Scheduler"]
    PG["🐘 PostgreSQL"]
    Worker1["💻 Worker Node 1"]
    Worker2["💻 Worker Node 2"]
    WS["📡 WebSocket Broker"]

    Browser -->|"REST (JWT)"| API
    Browser -->|"STOMP/SockJS"| WS
    API --> PG
    API --> Quartz
    Quartz -->|"Enqueue job"| PG
    Worker1 -->|"Poll + Execute"| PG
    Worker2 -->|"Poll + Execute"| PG
    Worker1 -->|"Push updates"| WS
    Worker2 -->|"Push updates"| WS
    API -->|"Push updates"| WS
    WS -->|"Live events"| Browser
```

---

## 3. Request Flow (REST API)

```mermaid
sequenceDiagram
    participant C as Client
    participant F as JwtAuthFilter
    participant S as Spring Security
    participant Ctrl as Controller
    participant Svc as Service
    participant DB as PostgreSQL

    C->>F: HTTP Request + Bearer Token
    F->>F: Validate JWT signature & expiry
    F->>S: Set SecurityContext with UserDetails
    S->>Ctrl: Route to @PreAuthorize-checked method
    Ctrl->>Svc: Business logic call
    Svc->>DB: JPA query / transaction
    DB-->>Svc: Result
    Svc-->>Ctrl: DTO response
    Ctrl-->>C: HTTP 200 JSON
```

---

## 4. Job Execution Flow

```mermaid
sequenceDiagram
    participant Client
    participant API as Spring Boot API
    participant PG as PostgreSQL
    participant Poller as QueuePoller
    participant Claim as JobClaimService
    participant Exec as JobExecutorService
    participant WS as WebSocket Broker

    Client->>API: POST /api/jobs {queueId, payload, type}
    API->>PG: INSERT INTO jobs (status='QUEUED')
    API-->>Client: 200 JobResponse
    API->>WS: broadcastJobUpdate(QUEUED)

    loop Every poll-interval-ms
        Poller->>Poller: Check activeCount < maxPoolSize
        Poller->>Claim: claimJob(queueId, workerId)
        Claim->>PG: SELECT ... FOR UPDATE SKIP LOCKED
        PG-->>Claim: Job row (locked)
        Claim->>PG: UPDATE status='CLAIMED', worker_id=?
        Claim-->>Poller: Optional<Job>
        Poller->>Exec: submit executeJob(jobId)
    end

    Exec->>PG: UPDATE status='RUNNING', started_at=NOW()
    Exec->>WS: broadcastJobUpdate(RUNNING)
    Note over Exec: Simulate job (sleep / random success or failure)
    
    alt Job succeeds
        Exec->>PG: UPDATE status='COMPLETED', completed_at=NOW()
        Exec->>WS: broadcastJobUpdate(COMPLETED)
    else Job fails
        Exec->>PG: Increment attempt_count
        Exec->>Exec: calculateRetryDelay(policy, attempt)
        Exec->>PG: UPDATE status='QUEUED', scheduled_at=now+delay
        Exec->>WS: broadcastJobUpdate(RETRYING)
        Note over Exec: OR if attempt >= max_attempts
        Exec->>PG: UPDATE status='DEAD_LETTER'
        Exec->>PG: INSERT INTO dead_letter_queue
    end
```

---

## 5. Worker Polling Flow

```mermaid
flowchart TD
    Start([Worker starts]) --> Register[Register in 'workers' table]
    Register --> Sync[syncPollers every 5s]
    Sync --> FetchQ[Fetch all unpaused queues from DB]
    FetchQ --> ForEach{For each queue}
    ForEach --> HasPoller{Poller exists?}
    HasPoller -- No --> CreatePool[Create ThreadPoolExecutor\ncoreSize = concurrencyLimit]
    CreatePool --> StartPoller[Start QueuePoller thread]
    HasPoller -- Yes --> CheckLimit{Limit changed?}
    CheckLimit -- Yes --> UpdatePool[Update pool size]
    CheckLimit -- No --> NextQ[Next queue]
    StartPoller --> PollLoop

    subgraph PollLoop[Poller Loop]
        Check{activeCount < maxPoolSize?}
        Check -- Yes --> Claim[claimJob SKIP LOCKED]
        Claim --> Found{Job found?}
        Found -- Yes --> Submit[Submit to ThreadPoolExecutor]
        Submit --> Check
        Found -- No --> Sleep[Sleep poll-interval-ms]
        Sleep --> Check
        Check -- No --> Wait[Sleep 200ms]
        Wait --> Check
    end
```

---

## 6. Heartbeat Flow

```mermaid
sequenceDiagram
    participant W as Worker Node
    participant PG as PostgreSQL
    participant WS as WebSocket Broker
    participant Reaper

    Note over W: @PostConstruct
    W->>PG: INSERT INTO workers (status=ACTIVE)
    W->>WS: broadcastWorkerUpdate(ACTIVE)

    loop Every 10 seconds
        W->>PG: UPDATE workers SET last_heartbeat_at=NOW()
        W->>PG: INSERT INTO worker_heartbeats (status=OK)
        W->>WS: broadcastWorkerUpdate
    end

    Note over Reaper: Every 30s — leader only
    Reaper->>PG: SELECT workers WHERE last_heartbeat_at < NOW()-30s
    PG-->>Reaper: [dead workers]
    Reaper->>PG: UPDATE workers SET status=DEAD
    Reaper->>PG: Recover jobs from dead workers

    Note over W: Graceful shutdown
    W->>PG: UPDATE workers SET status=OFFLINE
    W->>WS: broadcastWorkerUpdate(OFFLINE)
```

---

## 7. Retry Flow

```mermaid
flowchart TD
    Fail([Job fails]) --> HasPolicy{Queue has retry policy?}
    HasPolicy -- No --> DefaultDelay[Use 10s default delay]
    HasPolicy -- Yes --> Strategy{Strategy type?}
    Strategy -- FIXED --> FixedDelay["delay = base_delay"]
    Strategy -- LINEAR --> LinearDelay["delay = min(base + (n-1)*base, max)"]
    Strategy -- EXPONENTIAL --> ExpDelay["delay = min(base * 2^(n-1), max)"]
    DefaultDelay --> CheckAttempts
    FixedDelay --> CheckAttempts
    LinearDelay --> CheckAttempts
    ExpDelay --> CheckAttempts
    CheckAttempts{attempt_count < max_attempts?}
    CheckAttempts -- Yes --> Requeue["UPDATE status='QUEUED'\nscheduled_at = now + delay"]
    CheckAttempts -- No --> DLQ["UPDATE status='DEAD_LETTER'\nINSERT INTO dead_letter_queue"]
```

---

## 8. Dead Letter Queue Flow

```mermaid
flowchart LR
    MaxAttempts([Max attempts exceeded]) --> SetDL[Set job status = DEAD_LETTER]
    SetDL --> InsertDLQ[INSERT INTO dead_letter_queue\nfinal_error, moved_at]
    InsertDLQ --> Notify[Broadcast via WebSocket]
    Notify --> Dashboard[Dashboard shows in DLQ count]
    Dashboard --> Operator[Operator views job logs]
    Operator --> Retry[Click Retry in UI]
    Retry --> ResetJob["Reset attempt_count=0\nstatus=QUEUED\nscheduled_at=NOW()"]
    ResetJob --> Execution([Normal execution resumes])
```

---

## 9. Scheduler (Quartz) Flow

```mermaid
sequenceDiagram
    participant User
    participant API
    participant Quartz
    participant PG as PostgreSQL
    participant Launcher as QuartzJobLauncher

    User->>API: POST /api/projects/{id}/schedules
    API->>PG: INSERT INTO schedules (name, type, cron, ...)
    API->>Quartz: scheduler.scheduleJob(trigger)
    Quartz->>PG: Store trigger in QRTZ_* tables

    loop On trigger fire
        Quartz->>Launcher: execute(JobExecutionContext)
        Launcher->>PG: INSERT INTO jobs (status=QUEUED, type=RECURRING)
        Launcher->>WS: broadcastJobUpdate(QUEUED)
    end

    User->>API: PUT /api/schedules/{id}/pause
    API->>Quartz: scheduler.pauseTrigger(triggerKey)
    API->>PG: UPDATE schedules SET status=PAUSED

    User->>API: PUT /api/schedules/{id}/resume
    API->>Quartz: scheduler.resumeTrigger(triggerKey)
    API->>PG: UPDATE schedules SET status=ACTIVE
```

---

## 10. Database Interaction Summary

| Operation | Implementation | Why |
|-----------|---------------|-----|
| Job claiming | `SELECT FOR UPDATE SKIP LOCKED` | Atomic, no contention |
| Job execution record | `INSERT INTO job_executions` | Audit trail |
| Stats queries | Aggregate `COUNT` with `GROUP BY` status | Dashboard metrics |
| Reaper recovery | Full table scan on `workers.last_heartbeat_at` | Low frequency (30s) |
| Job pagination | `Spring Data Page<Job>` with `Specification` | Flexible filtering |
| Quartz state | `QRTZ_*` tables via JDBCJobStore | Persistent scheduling |
| Distributed locking | `locks` table with `ON CONFLICT DO UPDATE WHERE expires_at < NOW()` | Leader election |
