#!/bin/bash

echo "========== PHASE 3 TEST =========="

echo
echo "1. Docker Containers"
docker compose ps

echo
echo "2. Worker Heartbeats"
docker exec scheduler-postgres psql -U postgres -d scheduler -c \
"SELECT worker_id,status,last_heartbeat_at FROM worker_heartbeats ORDER BY worker_id,last_heartbeat_at DESC;"

echo
echo "3. Workers"
docker exec scheduler-postgres psql -U postgres -d scheduler -c \
"SELECT worker_id,status FROM workers;"

echo
echo "4. Recent Jobs"
docker exec scheduler-postgres psql -U postgres -d scheduler -c \
"SELECT id,status,worker_id,attempt_count FROM jobs ORDER BY id DESC LIMIT 20;"

echo
echo "5. Job Executions"
docker exec scheduler-postgres psql -U postgres -d scheduler -c \
"SELECT job_id,worker_id,status FROM job_executions ORDER BY id DESC LIMIT 20;"

echo
echo "6. Dead Letter Queue"
docker exec scheduler-postgres psql -U postgres -d scheduler -c \
"SELECT * FROM dead_letter_queue;"

echo
echo "7. Worker 1 Claims"
docker logs scheduler-worker-1 --tail=200 | grep "Job claimed"

echo
echo "8. Worker 2 Claims"
docker logs scheduler-worker-2 --tail=200 | grep "Job claimed"

echo
echo "9. Reaper Leader Election"
docker logs scheduler-worker-1 --tail=100 | grep "Leader lock"
docker logs scheduler-worker-2 --tail=100 | grep "Leader lock"

echo
echo "10. Retry Logs"
docker logs scheduler-worker-1 --tail=200 | grep -Ei "retry|failed"
docker logs scheduler-worker-2 --tail=200 | grep -Ei "retry|failed"

echo
echo "========== END =========="
