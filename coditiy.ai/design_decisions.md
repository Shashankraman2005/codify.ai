# DESIGN_DECISIONS.md — Architecture & Engineering Decisions

> This document explains every significant design and architectural decision made during the development of JOBSEEK. Each decision is presented with its rationale, trade-offs considered, and alternatives evaluated.

---

## 1. Why No Message Broker?

### Decision
Use PostgreSQL as the job queue store instead of a dedicated message broker (RabbitMQ, Kafka, SQS).

### Rationale
A job scheduler has fundamentally different requirements from a message bus:

- Jobs need **durable, queryable state** (status, attempt count, logs, scheduled time)
- Jobs need **conditional retry with backoff** — not just dead-lettering
- Jobs need **concurrency limits per queue**
- Dashboard needs **real-time stats** (aggregate queries, not just message counts)
- Operators need to **search, filter, cancel, and replay** individual jobs

All of these requirements are natural SQL operations. Implementing them on top of a message broker requires a separate database anyway, creating a dual-write consistency problem.

### Alternative Considered
RabbitMQ + separate PostgreSQL: rejected because it doubles operational complexity, introduces dual-write race conditions, and provides no benefit for this use case.

### Trade-off
PostgreSQL is not designed as a message queue and will not match the throughput of a dedicated broker at very high job volumes. For the target scale (thousands of jobs/minute, not millions), this is acceptable.

---

## 2. Atomic Job Claiming with `SELECT FOR UPDATE SKIP LOCKED`

### Decision
Workers claim jobs using a single SQL query:

```sql
SELECT * FROM jobs
WHERE queue_id = ?
  AND status = 'QUEUED'
  AND scheduled_at <= NOW()
ORDER BY priority DESC, scheduled_at ASC
LIMIT 1
FOR UPDATE SKIP LOCKED
```

### Rationale
`FOR UPDATE` acquires a row-level write lock. `SKIP LOCKED` causes the query to instantly skip any rows that are already locked by another transaction rather than blocking. This gives each worker an exclusive claim on a unique job in a single round-trip — no application-level coordination, no distributed locks.

### Why This Works
- **No duplicate processing**: Two workers can never claim the same job
- **No blocking**: Workers never wait for each other
- **Priority and delay**: `ORDER BY priority DESC, scheduled_at ASC` ensures high-priority and earliest-scheduled jobs are claimed first
- **Atomicity**: The update to `status = 'CLAIMED'` happens in the same transaction

### Alternative Considered
Optimistic locking with version counters: rejected because it creates retry storms under contention. SKIP LOCKED has O(1) contention behavior.

---

## 3. Worker Architecture: Pull-based vs Push-based

### Decision
Workers **pull** (poll) jobs from the database rather than being pushed work.

### Rationale
Pull-based polling is simpler, more fault-tolerant, and self-regulating:

- Workers only accept work they can handle (`activeCount < maxPoolSize`)
- No central coordinator that can become a bottleneck
- Workers that die simply stop pulling — no complex acknowledgment protocol
- Easy to scale horizontally: add more worker containers

The poll interval is configurable (`scheduler.worker.poll-interval-ms`, default: 1000 ms). Workers sleep the poll interval when no jobs are available, preventing busy-waiting.

### Per-Queue Thread Pools
Each queue gets its own `ThreadPoolExecutor` with `corePoolSize = maxPoolSize = concurrencyLimit`. A `SynchronousQueue` as the work queue (no buffering) means the poller only submits a job when a thread is immediately available. This enforces the concurrency limit exactly.

---

## 4. Spring Profiles: `api` vs `worker`

### Decision
The backend JAR is split into two active profiles:
- `api` — activates HTTP controllers, Quartz scheduler
- `worker` — activates `QueuePollerManager`, `WorkerHeartbeatService`, `ReaperService`

### Rationale
A single deployable artifact can serve as either an API server or a worker node (or both, for local development). This eliminates code duplication and simplifies the build pipeline. In Docker Compose, the API and worker containers use the same image but different `SPRING_PROFILES_ACTIVE` values.

---

## 5. Database Design Decisions

### Decision: PostgreSQL over MySQL

PostgreSQL's `FOR UPDATE SKIP LOCKED` is a first-class, stable feature. MySQL 8.0 added `SKIP LOCKED` but PostgreSQL's implementation is more mature and better documented. PostgreSQL also has superior support for JSONB, window functions, and advisory locks.

### Decision: Storing job payload as `TEXT`

Job payloads are stored as raw JSON strings (`TEXT`). This avoids JSONB column type complexity while still being human-readable. Validation of payload structure is the responsibility of the consuming application.

### Decision: Enum statuses as `VARCHAR`

`JobStatus`, `WorkerStatus`, etc. are stored as `VARCHAR(50)` rather than PostgreSQL native enums. VARCHAR enums are easier to migrate (adding a new status requires no schema change), easier to debug in raw SQL, and portable across databases.

### Decision: Timestamps with millisecond precision `TIMESTAMP(3)`

All timestamps use `TIMESTAMP(3)` (millisecond precision). This avoids rounding errors when comparing timestamps and ensures accurate ordering of closely-spaced events.

### Decision: Soft-deletes not used

Jobs are never deleted — they move through status transitions. This preserves a complete audit trail and simplifies the retry/replay feature.

---

## 6. Quartz JDBCJobStore

### Decision
Quartz is configured with `JDBCJobStore` (clustered) rather than `RAMJobStore`.

### Rationale
`RAMJobStore` loses all scheduled triggers on application restart. `JDBCJobStore` persists triggers to the same PostgreSQL database, so scheduled jobs survive restarts. Clustering (`isClustered: true`) ensures only one node fires each trigger even when multiple API instances are running.

### Implementation
The Quartz schema is installed by Flyway (`V2__Quartz_Schema.sql`). Quartz uses the PostgreSQL delegate (`PostgreSQLDelegate`) for dialect-specific optimizations.

### Trade-off
JDBCJobStore is significantly slower than RAMJobStore for very high-frequency jobs (sub-second intervals). For the typical scheduling use cases (every few seconds or more), this is not a concern.

---

## 7. Heartbeat Mechanism

### Decision
Each worker sends a heartbeat every 10 seconds by updating `workers.last_heartbeat_at` and inserting a row into `worker_heartbeats`.

### Rationale
- Simple, stateless health monitoring with no external dependency
- `workers.last_heartbeat_at` is the authoritative liveness timestamp
- `worker_heartbeats` provides a history for diagnosing intermittent connectivity issues
- Workers also send a final `status = OFFLINE` update on graceful shutdown (`@PreDestroy`)

### Threshold
The Reaper considers a worker dead after **30 seconds** without a heartbeat (`scheduler.reaper.dead-threshold-ms`). With a 10-second heartbeat interval, this gives 3 missed heartbeats before declaring death — a reasonable balance between liveness detection speed and transient network tolerance.

---

## 8. Dead Worker Reaper with Leader Election

### Decision
The Reaper runs on every worker node but uses a **distributed lock** (`locks` table) to ensure only one worker acts as the leader at a time.

### Rationale
If every worker ran the reaper independently, they would all attempt to recover the same dead jobs simultaneously, causing duplicate re-queuing. Leader election via `locks` table ensures exactly-once recovery.

### Lock Implementation
```sql
INSERT INTO locks (lock_name, holder_id, expires_at)
VALUES ('REAPER_LOCK', ?, NOW() + INTERVAL '35 seconds')
ON CONFLICT (lock_name) DO UPDATE
  SET holder_id = EXCLUDED.holder_id, expires_at = EXCLUDED.expires_at
  WHERE locks.expires_at < NOW()
```

The lock TTL (35 seconds) is slightly longer than the reaper interval (30 seconds), ensuring the leader keeps its lock unless it dies. If the leader dies, another worker wins the lock on the next reaper cycle.

---

## 9. Retry Strategy

### Decision
Three strategies are supported: FIXED, LINEAR, EXPONENTIAL.

### Formulas (actual implementation)

| Strategy | Formula | Example (base=5s, attempt=3) |
|----------|---------|-------------------------------|
| FIXED | `base_delay` | 5s |
| LINEAR | `min(base + (attempt-1) × base, max_delay)` | min(5 + 2×5, max) = 15s |
| EXPONENTIAL | `min(base × 2^(attempt-1), max_delay)` | min(5 × 4, max) = 20s |

All strategies cap at `max_delay_seconds`.

### Default
If no retry policy is attached to a queue, a hardcoded default of 10 seconds is used.

---

## 10. WebSocket Architecture

### Decision
Real-time updates are pushed via STOMP over WebSocket (SockJS fallback).

### Topics
- `/topic/jobs` — job status changes
- `/topic/queues` — queue state changes
- `/topic/workers` — worker heartbeat/status changes
- `/topic/events` — cluster event feed
- `/topic/dashboard` — aggregate metrics updates

### Rationale
Polling the REST API for updates would create unnecessary load and introduce latency. WebSocket push eliminates both. The SockJS fallback ensures compatibility with environments that block WebSocket connections.

---

## 11. Multi-tenancy Model

### Decision
The hierarchy is: User → Organization → Project → Queue → Job.

### Rationale
- **Organizations** represent companies or teams
- **Projects** represent applications or services within an org
- **Queues** represent logical job categories within a project

This allows a single JOBSEEK instance to serve multiple independent teams, each isolated within their own organization namespace.

### Authorization
`@PreAuthorize("@securityService.hasOrgRole(#orgId, 'ADMIN', 'MEMBER')")` enforces role-based access at each resource level. Roles are `ADMIN`, `MEMBER`, `VIEWER`.

---

## 12. Stuck Job Recovery

### Decision
In addition to dead worker recovery, the Reaper also recovers jobs stuck in `CLAIMED` status for longer than `scheduler.reaper.claim-timeout-ms` (default: 60 seconds).

### Rationale
A job can enter a stuck CLAIMED state if the worker crashes between claiming the job and starting execution (before setting status to RUNNING). Without this check, the job would stay claimed forever.

---

## 13. Batch Job Implementation

### Decision
A batch job is a parent job with multiple child jobs. The parent stays in `QUEUED` status until all children complete. If any child fails permanently (moves to `DEAD_LETTER`), the parent also becomes `DEAD_LETTER`.

### Rationale
Atomic fan-out with collective completion tracking. The parent job acts as an aggregate status record for the entire batch.

---

## 14. Graceful Shutdown

### Decision
`@PreDestroy` in `QueuePollerManager` stops all pollers and waits up to 15 seconds for in-flight jobs to complete before the JVM exits.

### Rationale
Without graceful shutdown, a SIGTERM during job execution would leave jobs in `RUNNING` state, where they would eventually be recovered by the Reaper. Graceful shutdown avoids this unnecessary recovery cycle and reduces the window for duplicate execution.

---

## 15. Global Exception Handler

### Decision
`GlobalExceptionHandler` (Phase 6) maps all exceptions to a consistent JSON error envelope:

```json
{
  "timestamp": "2026-07-04T01:00:00Z",
  "status": 400,
  "error": "Validation Failed",
  "message": "human-readable detail",
  "path": "/api/...",
  "requestId": "uuid"
}
```

### Rationale
Without this, Spring Boot's default error responses vary in shape and can expose stack traces. The consistent envelope makes frontend error handling straightforward.

---

## 16. Assumptions

- Jobs are idempotent (the platform may execute a job more than once in extreme failure scenarios)
- The job payload is a valid JSON string (not validated server-side)
- A single PostgreSQL instance is sufficient for the target scale
- Workers share the same codebase as the API (single artifact, multiple profiles)
