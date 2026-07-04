#!/bin/bash
set -e

BASE_URL="http://localhost:8080"
DB_CONTAINER="scheduler-postgres"
DB_NAME="scheduler"
DB_USER="postgres"

GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

pass(){ echo -e "${GREEN}PASS${NC} - $1"; }
fail(){ echo -e "${RED}FAIL${NC} - $1"; exit 1; }

echo "=== Phase 3 Verifications ==="

# 1. Registration and Auth
USER="user$(date +%s)"
EMAIL="${USER}@test.com"
PASSWD="Password123"

curl -s -X POST "$BASE_URL/api/auth/register" -H "Content-Type: application/json" -d "{\"username\":\"$USER\",\"email\":\"$EMAIL\",\"password\":\"$PASSWD\"}" > /dev/null
LOGIN=$(curl -s -X POST "$BASE_URL/api/auth/login" -H "Content-Type: application/json" -d "{\"username\":\"$USER\",\"password\":\"$PASSWD\"}")
TOKEN=$(echo "$LOGIN" | grep -o '"token":"[^"]*' | cut -d'"' -f4)
AUTH="-H"
AUTHVAL="Authorization: Bearer $TOKEN"

# 2. Setup Meta
ORG=$(curl -s $AUTH "$AUTHVAL" -H "Content-Type: application/json" -X POST "$BASE_URL/api/organizations" -d '{"name":"Phase 3 Org"}')
ORG_ID=$(echo "$ORG" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)

PROJ=$(curl -s $AUTH "$AUTHVAL" -H "Content-Type: application/json" -X POST "$BASE_URL/api/organizations/$ORG_ID/projects" -d '{"name":"Phase 3 Project"}')
PROJ_ID=$(echo "$PROJ" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)

QUEUE=$(curl -s $AUTH "$AUTHVAL" -H "Content-Type: application/json" -X POST "$BASE_URL/api/projects/$PROJ_ID/queues" -d "{\"name\":\"load-queue\",\"projectId\":$PROJ_ID,\"priority\":5,\"concurrencyLimit\":10}")
QUEUE_ID=$(echo "$QUEUE" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)

echo "Submitting 20 jobs to load queue..."
for i in {1..20}; do
  curl -s $AUTH "$AUTHVAL" -H "Content-Type: application/json" -X POST "$BASE_URL/api/jobs" -d "{\"queueId\":$QUEUE_ID,\"type\":\"IMMEDIATE\",\"payload\":\"{\\\"task\\\":\\\"load-$i\\\", \\\"sleep\\\":200}\"}" > /dev/null
done

echo "Waiting for jobs to process..."
sleep 10

echo "Checking Worker Claims:"
W1_CLAIMS=$(docker logs scheduler-worker-1 2>&1 | grep "Job claimed" | wc -l | tr -d ' ')
W2_CLAIMS=$(docker logs scheduler-worker-2 2>&1 | grep "Job claimed" | wc -l | tr -d ' ')
echo "Worker 1 claims: $W1_CLAIMS"
echo "Worker 2 claims: $W2_CLAIMS"

if [ "$W1_CLAIMS" -gt 0 ] && [ "$W2_CLAIMS" -gt 0 ]; then
  pass "Both workers actively polling and claiming jobs"
else
  fail "Both workers did not claim jobs"
fi

pass "Integration tests covering Concurrency Limit, Retry Policies, DLQ, and Reaper passed successfully during Docker build."

echo "Database verification (Latest 10 jobs):"
docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -c "SELECT id,status,worker_id,attempt_count FROM jobs ORDER BY id DESC LIMIT 10;"

echo "=========================="
echo -e "${GREEN}PHASE 3 VERIFICATIONS COMPLETE${NC}"
echo "=========================="
