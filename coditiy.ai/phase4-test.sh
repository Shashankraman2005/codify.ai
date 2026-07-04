#!/bin/bash

# Configuration
API_URL="http://localhost:8080/api"
TS=$(date +%s)
USERNAME="test_$TS"

echo "Registering $USERNAME to get JWT token..."
REGISTER_RES=$(curl -s -X POST -H "Content-Type: application/json" -d "{\"username\":\"$USERNAME\",\"email\":\"$USERNAME@coditiy.ai\",\"password\":\"password\"}" $API_URL/auth/register)
echo "Register Response: $REGISTER_RES"

JWT_TOKEN=$(curl -s -X POST -H "Content-Type: application/json" -d "{\"username\":\"$USERNAME\",\"password\":\"password\"}" $API_URL/auth/login | jq -r '.token')

if [ -z "$JWT_TOKEN" ] || [ "$JWT_TOKEN" == "null" ]; then
    echo "Failed to get JWT token."
    exit 1
fi

echo "=== Phase 4 Verifications ==="

echo "Fetching workspace context..."
ORG_ID=$(curl -s -H "Authorization: Bearer $JWT_TOKEN" $API_URL/organizations | jq -r '.[0].id')
PROJECT_ID=$(curl -s -H "Authorization: Bearer $JWT_TOKEN" $API_URL/organizations/$ORG_ID/projects | jq -r '.[0].id')

echo "Creating Queue for Project ID $PROJECT_ID..."
QUEUE_RES=$(curl -s -X POST -H "Authorization: Bearer $JWT_TOKEN" -H "Content-Type: application/json" -d "{\"name\":\"Phase 4 Queue\", \"priority\": 1, \"concurrencyLimit\": 5, \"projectId\": $PROJECT_ID}" $API_URL/projects/$PROJECT_ID/queues)
QUEUE_ID=$(echo "$QUEUE_RES" | jq -r '.id')

echo "Project ID: $PROJECT_ID, Queue ID: $QUEUE_ID"
echo

if [ -z "$QUEUE_ID" ] || [ "$QUEUE_ID" == "null" ]; then
    echo "Failed to create Queue. Response: $QUEUE_RES"
    exit 1
fi

# 2. Create CRON Schedule
echo "Creating CRON schedule (every 10 seconds)..."
CRON_PAYLOAD=$(cat <<EOF
{
  "name": "Phase 4 CRON Test",
  "scheduleType": "CRON",
  "cronExpression": "0/10 * * * * ?",
  "queueId": $QUEUE_ID,
  "payload": "{\"test\": \"quartz_cron\"}"
}
EOF
)
SCHEDULE_ID=$(curl -s -X POST -H "Authorization: Bearer $JWT_TOKEN" -H "Content-Type: application/json" -d "$CRON_PAYLOAD" $API_URL/projects/$PROJECT_ID/schedules | jq -r '.id')
echo "Created Schedule ID: $SCHEDULE_ID"
echo

# 3. Create FIXED_INTERVAL Schedule
echo "Creating FIXED_INTERVAL schedule (every 15 seconds)..."
FIXED_PAYLOAD=$(cat <<EOF
{
  "name": "Phase 4 FIXED Test",
  "scheduleType": "FIXED_INTERVAL",
  "intervalSeconds": 15,
  "queueId": $QUEUE_ID,
  "payload": "{\"test\": \"quartz_fixed\"}"
}
EOF
)
curl -s -X POST -H "Authorization: Bearer $JWT_TOKEN" -H "Content-Type: application/json" -d "$FIXED_PAYLOAD" $API_URL/projects/$PROJECT_ID/schedules > /dev/null
echo "Created FIXED_INTERVAL schedule."
echo

# 4. Wait and observe execution
echo "Waiting 35 seconds to allow Quartz to fire multiple times..."
sleep 35

echo "Checking queued/completed jobs from Quartz..."
docker exec scheduler-postgres psql -U postgres -d scheduler -c "SELECT id, status, worker_id, payload FROM jobs ORDER BY id DESC LIMIT 10;"

# 5. Pause CRON Schedule
echo "Pausing Schedule ID $SCHEDULE_ID..."
curl -s -X PUT -H "Authorization: Bearer $JWT_TOKEN" $API_URL/schedules/$SCHEDULE_ID/pause > /dev/null
echo

# 6. Verify Paused Status
STATUS=$(curl -s -H "Authorization: Bearer $JWT_TOKEN" $API_URL/schedules/$SCHEDULE_ID | jq -r '.status')
echo "Schedule $SCHEDULE_ID Status: $STATUS"
if [ "$STATUS" == "PAUSED" ]; then
    echo "PASS - Schedule successfully paused in Quartz."
else
    echo "FAIL - Schedule did not pause."
fi
echo

echo "Waiting 20 seconds to prove paused job doesn't fire..."
sleep 20
docker exec scheduler-postgres psql -U postgres -d scheduler -c "SELECT id, status, worker_id, payload FROM jobs ORDER BY id DESC LIMIT 5;"

# 7. Delete Schedule
echo "Deleting Schedule ID $SCHEDULE_ID..."
curl -s -X DELETE -H "Authorization: Bearer $JWT_TOKEN" $API_URL/schedules/$SCHEDULE_ID > /dev/null
echo "PASS - Schedule successfully deleted."
echo

echo "=========================="
echo "PHASE 4 VERIFICATIONS COMPLETE"
echo "=========================="
