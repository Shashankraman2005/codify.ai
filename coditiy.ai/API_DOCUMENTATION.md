# API_DOCUMENTATION.md — Complete REST API Reference

> **Base URL:** `http://localhost:8080`  
> **Authentication:** All endpoints except `/api/auth/**` and `/api/health` require `Authorization: Bearer <JWT>`

---

## Authentication

### POST /api/auth/register

Register a new user account. Automatically creates a default Organization and Project.

**Auth required:** No

**Request body:**
```json
{
  "username": "string (required)",
  "password": "string (required)",
  "email": "string (required)"
}
```

**Success response: `200 OK`**
```json
{ "message": "User registered successfully! Default Workspace created." }
```

**Error responses:**
| Status | Message |
|--------|---------|
| 400 | Username is already taken |
| 400 | Email is already in use |

**Example:**
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"Secret123!","email":"alice@example.com"}'
```

---

### POST /api/auth/login

Authenticate and receive a JWT token.

**Auth required:** No

**Request body:**
```json
{
  "username": "string",
  "password": "string"
}
```

**Success response: `200 OK`**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "id": 1,
  "username": "alice",
  "email": "alice@example.com"
}
```

**Error responses:**
| Status | Description |
|--------|-------------|
| 500 | Invalid username or password (Spring Security throws AuthenticationException) |

**Example:**
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"Secret123!"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")
```

---

## System

### GET /api/health

System health check. No authentication required. Used by Docker healthcheck.

**Auth required:** No

**Success response: `200 OK`**
```json
{
  "status": "UP",
  "database": "UP",
  "scheduler": "RUNNING"
}
```

---

## Organizations

### GET /api/organizations

Get all organizations the authenticated user is a member of.

**Auth required:** Yes

**Success response: `200 OK`**
```json
[
  {
    "id": 1,
    "name": "alice's Org",
    "createdAt": "2026-07-04T01:00:00",
    "updatedAt": "2026-07-04T01:00:00"
  }
]
```

---

### POST /api/organizations

Create a new organization. The authenticated user is automatically added as ADMIN.

**Auth required:** Yes

**Request body:**
```json
{ "name": "My New Org" }
```

**Success response: `200 OK`**
```json
{
  "id": 2,
  "name": "My New Org",
  "createdAt": "2026-07-04T01:00:00"
}
```

---

## Projects

### GET /api/organizations/{orgId}/projects

Get all projects within an organization.

**Auth required:** Yes (ADMIN, MEMBER, or VIEWER of org)

**Path parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| `orgId` | Long | Organization ID |

**Success response: `200 OK`**
```json
[
  { "id": 1, "name": "Default Project", "organizationId": 1 }
]
```

---

### POST /api/organizations/{orgId}/projects

Create a new project within an organization.

**Auth required:** Yes (ADMIN or MEMBER of org)

**Path parameters:** `orgId` — Organization ID

**Request body:**
```json
{ "name": "My Project" }
```

**Success response: `200 OK`**
```json
{ "id": 2, "name": "My Project", "organizationId": 1 }
```

---

## Queues

### GET /api/projects/{projectId}/queues

Get all queues for a project, including per-queue statistics.

**Auth required:** Yes (ADMIN, MEMBER, or VIEWER)

**Path parameters:** `projectId` — Project ID

**Success response: `200 OK`**
```json
[
  {
    "id": 1,
    "name": "image-processing",
    "projectId": 1,
    "projectName": "Default Project",
    "priority": 10,
    "concurrencyLimit": 5,
    "isPaused": false,
    "retryPolicyId": 1,
    "retryPolicyName": "exponential-3x",
    "stats": {
      "queuedCount": 3,
      "runningCount": 2,
      "completedCount": 45,
      "failedCount": 1,
      "averageExecutionTimeSeconds": 1.23
    }
  }
]
```

---

### POST /api/projects/{projectId}/queues

Create a new queue within a project.

**Auth required:** Yes (ADMIN)

**Path parameters:** `projectId` — Project ID

**Request body:**
```json
{
  "name": "email-sender",
  "priority": 5,
  "concurrencyLimit": 10,
  "retryPolicyId": 1
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | string | Yes | Queue name (unique per project) |
| `priority` | integer | Yes | Higher values = higher queue priority |
| `concurrencyLimit` | integer | Yes | Max parallel jobs per worker |
| `retryPolicyId` | Long | No | ID of retry policy to attach |

**Success response: `200 OK`** — QueueResponse (same shape as GET)

---

### PUT /api/queues/{id}

Update a queue's settings.

**Auth required:** Yes (ADMIN)

**Path parameters:** `id` — Queue ID

**Request body:** Same as POST

---

### POST /api/queues/{id}/pause

Pause a queue. Workers stop claiming new jobs from this queue. In-flight jobs are not affected.

**Auth required:** Yes (ADMIN)

**Success response: `200 OK`** — Updated QueueResponse with `isPaused: true`

---

### POST /api/queues/{id}/resume

Resume a paused queue.

**Auth required:** Yes (ADMIN)

**Success response: `200 OK`** — Updated QueueResponse with `isPaused: false`

---

## Retry Policies

### GET /api/retry-policies

List all retry policies (global, not project-scoped).

**Auth required:** Yes

**Success response: `200 OK`**
```json
[
  {
    "id": 1,
    "name": "exponential-3x",
    "strategyType": "EXPONENTIAL",
    "baseDelaySeconds": 10,
    "maxDelaySeconds": 300,
    "maxAttempts": 3
  }
]
```

---

### POST /api/retry-policies

Create a new retry policy.

**Auth required:** Yes

**Request body:**
```json
{
  "name": "fixed-5s-retry",
  "strategyType": "FIXED",
  "baseDelaySeconds": 5,
  "maxDelaySeconds": 60,
  "maxAttempts": 5
}
```

| Field | Type | Values | Description |
|-------|------|--------|-------------|
| `strategyType` | string | `FIXED`, `LINEAR`, `EXPONENTIAL` | Backoff algorithm |
| `baseDelaySeconds` | integer | ≥ 1 | Base delay for calculation |
| `maxDelaySeconds` | integer | ≥ 1 | Cap on computed delay |
| `maxAttempts` | integer | ≥ 1 | Jobs exceeding this go to DLQ |

**Success response: `200 OK`** — Created RetryPolicy

---

## Jobs

### POST /api/jobs

Dispatch a new job to a queue.

**Auth required:** Yes (ADMIN or MEMBER of queue's project)

**Request body:**
```json
{
  "queueId": 1,
  "type": "IMMEDIATE",
  "payload": "{\"task\": \"resize_image\", \"imageId\": 42}",
  "priority": 5,
  "delaySeconds": null,
  "scheduledAt": null
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `queueId` | Long | Yes | Target queue ID |
| `type` | string | Yes | `IMMEDIATE`, `DELAYED`, `SCHEDULED`, `BATCH` |
| `payload` | string | Yes | JSON string payload for the worker |
| `priority` | integer | No | Higher = claimed first (default: 0) |
| `delaySeconds` | integer | Conditional | Required for `DELAYED` type |
| `scheduledAt` | ISO datetime | Conditional | Required for `SCHEDULED` type |
| `childJobs` | array | Conditional | Required for `BATCH` type |

**Batch example:**
```json
{
  "queueId": 1,
  "type": "BATCH",
  "payload": "",
  "childJobs": [
    {"queueId": 1, "type": "IMMEDIATE", "payload": "{\"item\": 1}"},
    {"queueId": 1, "type": "IMMEDIATE", "payload": "{\"item\": 2}"}
  ]
}
```

**Success response: `200 OK`**
```json
{
  "id": 42,
  "queueId": 1,
  "type": "IMMEDIATE",
  "status": "QUEUED",
  "payload": "{\"task\": \"resize_image\"}",
  "priority": 5,
  "attemptCount": 0,
  "maxAttempts": 3,
  "scheduledAt": "2026-07-04T01:00:00",
  "createdAt": "2026-07-04T01:00:00"
}
```

---

### GET /api/jobs

List jobs with optional filters. Returns a paginated result.

**Auth required:** Yes

**Query parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| `queueId` | Long | Filter by queue |
| `status` | string | `QUEUED`, `CLAIMED`, `RUNNING`, `COMPLETED`, `FAILED`, `DEAD_LETTER`, `SCHEDULED` |
| `type` | string | `IMMEDIATE`, `DELAYED`, `SCHEDULED`, `RECURRING`, `BATCH` |
| `startDate` | ISO datetime | Filter by `createdAt >= startDate` |
| `endDate` | ISO datetime | Filter by `createdAt <= endDate` |
| `page` | int | Page number (0-indexed, default: 0) |
| `size` | int | Page size (default: 20) |

**Success response: `200 OK`**
```json
{
  "content": [ ...JobResponse... ],
  "totalPages": 5,
  "totalElements": 96,
  "number": 0,
  "size": 20
}
```

---

### GET /api/jobs/{id}

Get a specific job by ID.

**Auth required:** Yes (VIEWER or above)

**Success response: `200 OK`** — JobResponse

---

### GET /api/jobs/{id}/logs

Get all log entries for a specific job.

**Auth required:** Yes (VIEWER or above)

**Success response: `200 OK`**
```json
[
  {
    "id": 1,
    "jobId": 42,
    "logLevel": "INFO",
    "message": "Job claimed by worker-node-1 (Attempt 1/3)",
    "createdAt": "2026-07-04T01:00:01"
  },
  {
    "id": 2,
    "jobId": 42,
    "logLevel": "INFO",
    "message": "Job execution started",
    "createdAt": "2026-07-04T01:00:01"
  }
]
```

---

### POST /api/jobs/{id}/cancel

Cancel a job in `QUEUED`, `SCHEDULED`, or `RUNNING` status.

**Auth required:** Yes (ADMIN or MEMBER)

**Success response: `200 OK`** — Updated JobResponse with `status: FAILED`

---

### POST /api/jobs/{id}/retry

Re-queue a job that is in `FAILED` or `DEAD_LETTER` status. Resets `attemptCount` to 0.

**Auth required:** Yes (ADMIN)

**Success response: `200 OK`** — Updated JobResponse with `status: QUEUED`

---

## Schedules (Quartz)

### POST /api/projects/{projectId}/schedules

Register a new Quartz-managed schedule for a project.

**Auth required:** Yes

**Path parameters:** `projectId` — Project ID

**Request body:**
```json
{
  "name": "daily-cleanup",
  "scheduleType": "CRON",
  "cronExpression": "0 0 2 * * ?",
  "intervalSeconds": null,
  "scheduledAt": null,
  "payload": "{\"cleanup\": true}",
  "queueId": 1
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | string | Yes | Unique name per project |
| `scheduleType` | string | Yes | `CRON`, `FIXED_INTERVAL`, `ONE_TIME`, `DELAYED` |
| `cronExpression` | string | Conditional | Quartz CRON (7-field with seconds). Required for `CRON` type |
| `intervalSeconds` | integer | Conditional | Required for `FIXED_INTERVAL` |
| `scheduledAt` | ISO datetime | Conditional | Required for `ONE_TIME` and `DELAYED` |
| `payload` | string | Yes | JSON payload to inject into triggered jobs |
| `queueId` | Long | Yes | Target queue for triggered jobs |

**Success response: `201 Created`**
```json
{
  "id": 3,
  "name": "daily-cleanup",
  "scheduleType": "CRON",
  "cronExpression": "0 0 2 * * ?",
  "status": "ACTIVE",
  "nextFireTime": "2026-07-05T02:00:00",
  "projectId": 1,
  "queueId": 1
}
```

---

### GET /api/projects/{projectId}/schedules

List all schedules for a project.

**Auth required:** Yes

**Success response: `200 OK`** — Array of ScheduleResponse

---

### GET /api/schedules/{id}

Get a specific schedule.

**Auth required:** Yes

**Success response: `200 OK`** — ScheduleResponse

---

### DELETE /api/schedules/{id}

Delete a schedule and remove it from Quartz.

**Auth required:** Yes

**Success response: `204 No Content`**

---

### PUT /api/schedules/{id}/pause

Pause a Quartz trigger. The schedule remains registered but will not fire.

**Auth required:** Yes

**Success response: `200 OK`** — Updated ScheduleResponse with `status: PAUSED`

---

### PUT /api/schedules/{id}/resume

Resume a paused Quartz trigger.

**Auth required:** Yes

**Success response: `200 OK`** — Updated ScheduleResponse with `status: ACTIVE`

---

## Workers

### GET /api/workers

List all worker nodes that have ever registered.

**Auth required:** Yes

**Success response: `200 OK`**
```json
[
  {
    "workerId": "worker-node-1",
    "hostname": "scheduler-worker-1",
    "status": "ACTIVE",
    "lastHeartbeatAt": "2026-07-04T01:05:00",
    "createdAt": "2026-07-04T01:00:00"
  }
]
```

---

## Dashboard

### GET /api/dashboard/stats

Get aggregate metrics for a project, including a 24-hour throughput chart.

**Auth required:** Yes

**Query parameters:**
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `projectId` | Long | Yes | Project to fetch stats for |

**Success response: `200 OK`**
```json
{
  "totalQueues": 3,
  "runningCount": 2,
  "queuedCount": 8,
  "completedCount": 127,
  "failedCount": 4,
  "throughputChart": [
    { "hour": "01:00", "completed": 12, "failed": 0 },
    { "hour": "02:00", "completed": 8, "failed": 1 },
    ...
  ]
}
```

---

## Standard Error Envelope

All API errors return a consistent JSON envelope:

```json
{
  "timestamp": "2026-07-04T01:00:00Z",
  "status": 400,
  "error": "Validation Failed",
  "message": "Human-readable detail",
  "path": "/api/jobs",
  "requestId": "eea64ff5-614b-4fea-a429-55f9f9a247cf"
}
```

| HTTP Status | Error String | Trigger |
|-------------|-------------|---------|
| 400 | Validation Failed | `@Valid` constraint violation |
| 400 | Bad Request | `IllegalArgumentException` |
| 401 | Unauthorized | Missing or invalid JWT |
| 403 | Access Denied | `@PreAuthorize` check failed |
| 404 | Not Found | Entity not found |
| 409 | Conflict | Duplicate key constraint |
| 500 | Internal Server Error | Unexpected exception |

The `requestId` matches the `X-Request-Id` response header for log correlation.

---

## WebSocket Subscriptions

Connect via: `ws://localhost:8080/ws` (SockJS: `http://localhost:8080/ws`)

| Topic | Payload | Description |
|-------|---------|-------------|
| `/topic/jobs` | JobResponse | Any job status change |
| `/topic/queues` | QueueResponse | Queue pause/resume/update |
| `/topic/workers` | WorkerResponse | Worker heartbeat/status change |
| `/topic/events` | `{type, message, timestamp}` | Cluster event feed |
| `/topic/dashboard` | DashboardStats | Metrics update |
