# DEPLOYMENT.md — Production & Development Deployment Guide

---

## 🐋 1. Docker Compose Stack (Local & Staging)

The project includes a ready-to-run multi-container orchestration system via `docker-compose.yml`.

### Services Structure
- `scheduler-postgres`: PostgreSQL 15 Database (runs migrations via Flyway, healthy state checked via `pg_isready`).
- `scheduler-api`: Spring Boot API Server (runs REST controller, WebSocket endpoint, and Quartz trigger controller).
- `scheduler-worker-1`: Pull-based background job execution worker node.
- `scheduler-worker-2`: Secondary background job execution worker node to demonstrate load distribution.
- `scheduler-dashboard`: React + Nginx static server hosting the frontend interface on port `3001`.

### Build & Startup Commands

```bash
# Build all images and start in background
docker compose up --build -d

# Show current logs of the API
docker compose logs -f scheduler-api

# Show worker execution logs
docker compose logs -f scheduler-worker-1

# Stop and remove containers
docker compose down

# Stop and wipe volume database directories
docker compose down -v
```

---

## ⚙️ 2. Environment Configurations

Production deployment requires replacing the development defaults. You can specify these variables in a `.env` file in the root workspace folder:

```env
# Database Credentials
POSTGRES_DB=scheduler
POSTGRES_USER=postgres
POSTGRES_PASSWORD=secureproductionpwd

# Datasource Connection URL (For Spring Boot overrides if deploying separately)
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres-hostname:5432/scheduler
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=secureproductionpwd

# Secret used to sign JWT Tokens (Must be a 256-bit or greater cryptographically secure random string)
JWT_SECRET=9yB_E_H_McQfTjWnZr4u7x_A_C_F_JaNdRgUkXp2s5v8y_B_D_G_KbPeShVmYq3_very_long_secret_key_32_bytes

# WebSocket Config
WEBSOCKET_ALLOWED_ORIGINS=http://yourdomain.com
```

---

## 🛠️ 3. Production Deployment Step-by-Step

When deploying to production, follow these steps to secure and isolate the stack:

### Step 1: Secure Database Credentials
Never run production databases using the default `postgrespassword`. Update the DB password inside your secret manager or `.env`.

### Step 2: Separate Worker from API Nodes (Scale Vertically/Horizontally)
In production, you should run distinct server groups for API endpoints and Background Workers. You can restrict which profiles load on specific instances via environment flags:

**API-only Node:**
Set environmental profile: `SPRING_PROFILES_ACTIVE=api` (does not poll queues, only serves REST endpoints, processes Quartz triggers, and broadcasts messages).

**Worker-only Node:**
Set environmental profile: `SPRING_PROFILES_ACTIVE=worker` (polls database queues and executes tasks).

### Step 3: Run behind a Reverse Proxy / HTTPS load balancer
Configure an ingress proxy (e.g. Nginx or Traefik) to secure standard ports, term SSL certificates (HTTPS/WSS), and map:
- `/api/**` -> `http://scheduler-api:8080/api/**`
- `/ws/**` -> `http://scheduler-api:8080/ws/**` (requires WebSocket upgrade headers)
- `/*` -> `http://scheduler-dashboard:80/*`

---

## 🩺 4. Health Checks & Monitoring

The API provides a health check endpoint: `GET /api/health`

### Expected Health Check Output (200 OK)
```json
{
  "status": "UP",
  "database": "UP",
  "scheduler": "RUNNING"
}
```

### Docker Healthcheck Implementation
In `docker-compose.yml`, the API relies on this healthcheck:
```yaml
healthcheck:
  test: ["CMD", "curl", "-f", "http://localhost:8080/api/health"]
  interval: 20s
  timeout: 5s
  retries: 3
  start_period: 40s
```

---

## 📋 5. Troubleshooting & Logs

- **MDC Log Tracing**: Every log generated inside the API or Worker is prefixed with a `[jobId=X]` context marker. If you view raw terminal outputs or route them to a centralized collector (Splunk/ELK), filter by `jobId=2` to view every claim attempt, status change, and log associated with Job 2.
- **Quartz Scheduling issues**: If Quartz triggers are not firing, check database connection locks. Ensure there are no deadlock locks inside table `QRTZ_LOCKS`.
- **WebSocket disconnects**: Ensure your proxy (Nginx) has configuration support for `Connection "Upgrade"` and `Upgrade $http_upgrade` headers.
