#!/bin/bash
set -e

echo "======================================"
echo "Phase 5 Verification Script"
echo "======================================"

echo "[1/4] Rebuilding and starting Docker Compose..."
docker compose down -v
docker compose build --no-cache
docker compose up -d

echo "[2/4] Waiting for services to become healthy..."
sleep 20

echo "[3/4] Verifying Dashboard Metrics API..."
curl -s http://localhost:8080/api/dashboard/metrics/1 | grep -q "jobsCompletedToday"
echo "✅ Dashboard Metrics API is returning data."

echo "[4/4] Verifying WebSocket STOMP endpoint..."
# We can just check if the /ws/info endpoint (SockJS info) is up
HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/ws/info)
if [ "$HTTP_STATUS" -eq 200 ]; then
  echo "✅ WebSocket STOMP /ws endpoint is accessible."
else
  echo "❌ Failed to connect to WebSocket /ws endpoint. HTTP Status: $HTTP_STATUS"
  exit 1
fi

echo ""
echo "Phase 5 integration test passed!"
echo "You can now run the React Dashboard (cd dashboard && npm run dev) to see Live Event Feed."
echo "======================================"
