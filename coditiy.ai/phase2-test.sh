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

echo "=== Phase 2 Smoke Test ==="

command -v jq >/dev/null || fail "jq is required (brew install jq)"

docker compose ps >/dev/null || fail "Docker Compose not running"
pass "Docker Compose"

curl -sf "$BASE_URL/v3/api-docs" >/dev/null || fail "Swagger unavailable"
pass "Swagger"

USER="user$(date +%s)"
EMAIL="${USER}@test.com"
PASSWD="Password123"

echo "Registering user..."
REG=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/auth/register" \
-H "Content-Type: application/json" \
-d "{\"username\":\"$USER\",\"email\":\"$EMAIL\",\"password\":\"$PASSWD\"}")
REG_CODE=$(echo "$REG" | tail -n1)
[ "$REG_CODE" = "200" ] || [ "$REG_CODE" = "201" ] || fail "Registration ($REG_CODE)"
pass "Registration"

echo "Logging in..."
LOGIN=$(curl -s -X POST "$BASE_URL/api/auth/login" \
-H "Content-Type: application/json" \
-d "{\"username\":\"$USER\",\"password\":\"$PASSWD\"}")

TOKEN=$(echo "$LOGIN" | jq -r '.accessToken // .token // .jwt // .access_token // empty')
[ -n "$TOKEN" ] || fail "JWT not found in login response: $LOGIN"
pass "Login/JWT"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/jobs")
if [ "$STATUS" = "401" ] || [ "$STATUS" = "403" ]; then
  pass "Protected endpoint blocks anonymous ($STATUS)"
else
  fail "Anonymous protection unexpected ($STATUS)"
fi

AUTH="-H"
AUTHVAL="Authorization: Bearer $TOKEN"

AUTH_STATUS=$(curl -s -o /dev/null -w "%{http_code}" $AUTH "$AUTHVAL" "$BASE_URL/api/jobs")
[ "$AUTH_STATUS" = "200" ] || fail "Authorized GET /api/jobs ($AUTH_STATUS)"
pass "Authorized access"

echo "Creating organization..."
ORG=$(curl -s $AUTH "$AUTHVAL" -H "Content-Type: application/json" \
-X POST "$BASE_URL/api/organizations" \
-d '{"name":"QA Organization"}')

ORG_ID=$(echo "$ORG" | jq -r '.id // empty')
[ -n "$ORG_ID" ] || fail "Organization creation failed: $ORG"
pass "Organization"

echo "Creating project..."
PROJ=$(curl -s $AUTH "$AUTHVAL" -H "Content-Type: application/json" \
-X POST "$BASE_URL/api/organizations/$ORG_ID/projects" \
-d '{"name":"QA Project"}')

PROJ_ID=$(echo "$PROJ" | jq -r '.id // empty')
[ -n "$PROJ_ID" ] || fail "Project creation failed: $PROJ"
pass "Project"

echo "Creating queue..."
QUEUE=$(curl -s $AUTH "$AUTHVAL" -H "Content-Type: application/json" \
-X POST "$BASE_URL/api/projects/$PROJ_ID/queues" \
-d "{\"name\":\"default\",\"projectId\":$PROJ_ID,\"priority\":5,\"concurrencyLimit\":2}")

QUEUE_ID=$(echo "$QUEUE" | jq -r '.id // empty')
[ -n "$QUEUE_ID" ] || fail "Queue creation failed: $QUEUE"
pass "Queue"

echo "Creating job..."
JOB=$(curl -s $AUTH "$AUTHVAL" -H "Content-Type: application/json" \
-X POST "$BASE_URL/api/jobs" \
-d "{\"queueId\":$QUEUE_ID,\"type\":\"IMMEDIATE\",\"payload\":\"{\\\"task\\\":\\\"demo\\\"}\"}")

JOB_ID=$(echo "$JOB" | jq -r '.id // empty')
[ -n "$JOB_ID" ] || fail "Job creation failed: $JOB"
pass "Job"

sleep 5

echo "Worker logs:"
docker logs scheduler-worker-1 --tail=20 || true
docker logs scheduler-worker-2 --tail=20 || true

echo "Database summary:"
docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -c "\dt"
docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -c "select count(*) as users from users;"
docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -c "select count(*) as jobs from jobs;"

echo
echo "=========================="
echo -e "${GREEN}PHASE 2 SMOKE TEST COMPLETE${NC}"
echo "=========================="
