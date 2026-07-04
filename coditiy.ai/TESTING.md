# TESTING.md — Testing Strategy and Reference

---

## Testing Strategy

JOBSEEK's testing approach is layered:

| Layer | What is tested | Tool |
|-------|---------------|------|
| Unit | Retry backoff formulas, service logic | JUnit 5 |
| Integration | Full Spring context, DB transactions, job lifecycle | Spring Boot Test + TestContainers (or embedded H2) |
| Manual Functional | REST API flows, dashboard interaction | curl + browser |
| Concurrency | SKIP LOCKED, no duplicates under contention | Manual Docker test |
| Failure | Dead worker recovery, stuck job recovery | Kill container mid-execution |
| Retry | All three backoff strategies | Integration test |

---

## 1. Unit Tests

### RetryBackoffCalculator

The retry delay formulas are the most algorithmically sensitive unit.

**Expected behaviour:**

| Strategy | Base | Max | Attempt | Expected Delay |
|----------|------|-----|---------|---------------|
| FIXED | 10 | 60 | 1 | 10 |
| FIXED | 10 | 60 | 3 | 10 |
| LINEAR | 5 | 60 | 1 | 5 |
| LINEAR | 5 | 60 | 3 | 15 |
| LINEAR | 5 | 20 | 5 | 20 (capped) |
| EXPONENTIAL | 5 | 300 | 1 | 5 |
| EXPONENTIAL | 5 | 300 | 3 | 20 |
| EXPONENTIAL | 5 | 300 | 5 | 80 |
| EXPONENTIAL | 5 | 50 | 5 | 50 (capped) |
| null policy | n/a | n/a | any | 10 (default) |

**Test skeleton:**
```java
@Test
void fixedBackoff_alwaysReturnsBaseDelay() {
    RetryPolicy policy = RetryPolicy.builder()
        .strategyType(RetryStrategy.FIXED)
        .baseDelaySeconds(10)
        .maxDelaySeconds(60)
        .build();
    assertEquals(10, calculator.calculateDelay(policy, 1));
    assertEquals(10, calculator.calculateDelay(policy, 5));
}

@Test
void exponentialBackoff_capsAtMaxDelay() {
    RetryPolicy policy = RetryPolicy.builder()
        .strategyType(RetryStrategy.EXPONENTIAL)
        .baseDelaySeconds(5)
        .maxDelaySeconds(50)
        .build();
    assertTrue(calculator.calculateDelay(policy, 10) <= 50);
}
```

---

## 2. Integration Tests

### Job Lifecycle Test

Tests the complete flow: dispatch → claim → execute → complete.

```bash
# Run all integration tests
./mvnw test -Dtest="*IntegrationTest"
```

**Expected flow:**

1. `POST /api/jobs` → response: `{ "status": "QUEUED" }`
2. Worker polls within 1 second → job transitions to `CLAIMED`
3. Worker starts execution → `RUNNING`
4. Job completes → `COMPLETED`
5. `GET /api/jobs/{id}` returns `COMPLETED`
6. `GET /api/jobs/{id}/logs` returns at least 4 log entries

---

### Retry Mechanism Test

1. Configure a queue with `maxAttempts = 3`, FIXED retry, 2s base delay
2. Dispatch a job whose payload triggers failure (e.g., `{"fail": true}`)
3. Observe: 3 attempts, status transitions `RUNNING → QUEUED → RUNNING → QUEUED → RUNNING → DEAD_LETTER`
4. Verify `dead_letter_queue` has one record for the job

---

### Batch Job Test

1. Dispatch a BATCH job with 3 children
2. Verify parent stays in `QUEUED` while children execute
3. Verify parent transitions to `COMPLETED` once all children complete
4. If any child fails permanently → parent becomes `DEAD_LETTER`

---

### Queue Concurrency Test

1. Create a queue with `concurrencyLimit = 2`
2. Dispatch 10 immediate jobs
3. Monitor: never more than 2 jobs in `RUNNING` state simultaneously
4. All 10 complete in order

---

## 3. Manual Functional Testing

### Full Workflow Walkthrough

```bash
# Step 1 — Start the stack
docker compose up --build -d

# Step 2 — Verify health
curl http://localhost:8080/api/health
# Expected: {"status":"UP","database":"UP","scheduler":"RUNNING"}

# Step 3 — Register
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"Test1234!","email":"test@example.com"}'
# Expected: {"message":"User registered successfully! Default Workspace created."}

# Step 4 — Login and extract token
export TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"Test1234!"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

# Step 5 — Get organizations
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/organizations
# Expected: [{...org object...}]

# Step 6 — Get projects (use orgId from step 5)
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/organizations/1/projects

# Step 7 — Create retry policy
curl -X POST http://localhost:8080/api/retry-policies \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"exp-backoff","strategyType":"EXPONENTIAL","baseDelaySeconds":5,"maxDelaySeconds":60,"maxAttempts":3}'

# Step 8 — Create queue
curl -X POST http://localhost:8080/api/projects/1/queues \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"test-queue","priority":1,"concurrencyLimit":5,"retryPolicyId":1}'

# Step 9 — Dispatch job
curl -X POST http://localhost:8080/api/jobs \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"queueId":1,"type":"IMMEDIATE","payload":"{\"task\":\"test\"}","priority":1}'

# Step 10 — Watch job status (poll a few times)
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/jobs/1

# Step 11 — Get job logs
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/jobs/1/logs
```

---

## 4. Concurrency Testing

Verifies that two workers do NOT process the same job.

```bash
# 1. Start both workers
docker compose up --build -d

# 2. Dispatch 50 jobs to the same queue
for i in {1..50}; do
  curl -s -X POST http://localhost:8080/api/jobs \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"queueId":1,"type":"IMMEDIATE","payload":"{\"n\":'$i'}","priority":1}' > /dev/null
done

# 3. After all jobs complete, verify no job was executed twice
docker exec scheduler-postgres psql -U postgres -d scheduler \
  -c "SELECT job_id, COUNT(*) c FROM job_executions GROUP BY job_id HAVING c > 1;"
# Expected: 0 rows (no duplicates)

# 4. Verify both workers processed jobs
docker exec scheduler-postgres psql -U postgres -d scheduler \
  -c "SELECT worker_id, COUNT(*) FROM job_executions GROUP BY worker_id;"
# Expected: both worker-node-1 and worker-node-2 have counts > 0
```

---

## 5. Worker Failure Testing

### Dead Worker Recovery

```bash
# 1. Dispatch 10 jobs to ensure worker-1 picks some up
# 2. Kill worker-1 mid-execution
docker stop scheduler-worker-1

# 3. Wait for Reaper to run (max 30 seconds)
sleep 35

# 4. Verify:
# - worker-1 status = DEAD
# - Jobs previously assigned to worker-1 are re-queued or moved to DLQ
docker exec scheduler-postgres psql -U postgres -d scheduler \
  -c "SELECT status FROM workers WHERE worker_id='worker-node-1';"
# Expected: DEAD

docker exec scheduler-postgres psql -U postgres -d scheduler \
  -c "SELECT status, worker_id FROM jobs WHERE worker_id='worker-node-1';"
# Expected: status = QUEUED (re-queued) or DEAD_LETTER (max attempts exceeded)
```

---

## 6. Queue Pause Testing

```bash
# 1. Pause the queue
curl -X POST http://localhost:8080/api/queues/1/pause \
  -H "Authorization: Bearer $TOKEN"
# Expected: {"isPaused": true}

# 2. Dispatch a new job
curl -X POST http://localhost:8080/api/jobs \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"queueId":1,"type":"IMMEDIATE","payload":"{}","priority":1}'

# 3. Verify job stays QUEUED (workers don't claim from paused queues)
sleep 3
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/jobs/latest
# Expected: status = QUEUED

# 4. Resume queue
curl -X POST http://localhost:8080/api/queues/1/resume \
  -H "Authorization: Bearer $TOKEN"

# 5. Job should now be claimed and executed within poll-interval-ms
```

---

## 7. Quartz Scheduling Test

```bash
# Register a 10-second fixed interval schedule
curl -X POST http://localhost:8080/api/projects/1/schedules \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "test-recurring",
    "scheduleType": "FIXED_INTERVAL",
    "intervalSeconds": 10,
    "payload": "{\"scheduled\": true}",
    "queueId": 1
  }'

# Wait 30 seconds — should fire 3 times
sleep 30

# Verify recurring jobs were created
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/jobs?type=RECURRING" | python3 -m json.tool
# Expected: at least 2-3 jobs with type=RECURRING
```

---

## 8. Error Handling Tests

### Validation error
```bash
curl -X POST http://localhost:8080/api/retry-policies \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":""}' 
# Expected: 400 {"error":"Validation Failed","message":"..."}
```

### Unauthorized access
```bash
curl http://localhost:8080/api/organizations
# Expected: 401
```

### Duplicate resource
```bash
# Create a queue named "test-queue" twice
# Expected on second call: 409 {"error":"Conflict"}
```

---

## 9. Expected Outputs Summary

| Test | Expected Result |
|------|----------------|
| Health check | `{"status":"UP","database":"UP","scheduler":"RUNNING"}` |
| Register | `{"message":"User registered successfully!"}` |
| Login | JSON with `token`, `id`, `username`, `email` |
| Create org | Org object with `id` |
| Create queue | Queue object with `stats` embedded |
| Dispatch job | `{"status":"QUEUED"}` |
| Job after worker polls | `{"status":"COMPLETED"}` |
| Job logs | Array with INFO entries (claim, start, complete) |
| 50 concurrent jobs | No `job_executions` row with `COUNT(*) > 1` |
| Dead worker | Jobs re-queued; worker status = DEAD |
| Bad password | 500 Internal Server Error (Spring Security) |
| Invalid JWT | 401 Unauthorized |
| Quartz schedule | New RECURRING job appears every interval |

---

## 10. Coverage Summary

| Area | Coverage Method | Status |
|------|----------------|--------|
| Retry formulas | Unit test | ✅ Verifiable |
| Job lifecycle | Integration test | ✅ Verifiable |
| SKIP LOCKED deduplication | Concurrency test | ✅ Verifiable |
| Worker heartbeat | Manual + logs | ✅ Verifiable |
| Reaper recovery | Container kill test | ✅ Verifiable |
| Queue pause/resume | API test | ✅ Verifiable |
| Quartz scheduling | API + wait test | ✅ Verifiable |
| Auth / JWT | Integration test | ✅ Verifiable |
| Error responses | API test | ✅ Verifiable |
| WebSocket live updates | Browser dashboard | ✅ Visual |
