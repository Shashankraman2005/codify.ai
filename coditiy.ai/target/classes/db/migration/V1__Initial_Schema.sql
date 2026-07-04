-- Create users table
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);

-- Create organizations table
CREATE TABLE organizations (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);

-- Create organization_members table
CREATE TABLE organization_members (
    id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(50) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_org_mem_org FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT fk_org_mem_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_org_user UNIQUE (organization_id, user_id)
);

-- Create projects table
CREATE TABLE projects (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    organization_id BIGINT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_projects_org FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT
);

-- Create retry_policies table
CREATE TABLE retry_policies (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    strategy_type VARCHAR(50) NOT NULL,
    base_delay_seconds INTEGER NOT NULL,
    max_delay_seconds INTEGER NOT NULL,
    max_attempts INTEGER NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);

-- Create queues table
CREATE TABLE queues (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    project_id BIGINT NOT NULL,
    priority INTEGER NOT NULL DEFAULT 0,
    concurrency_limit INTEGER NOT NULL DEFAULT 5,
    retry_policy_id BIGINT NULL,
    is_paused BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_queues_proj FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE RESTRICT,
    CONSTRAINT fk_queues_retry FOREIGN KEY (retry_policy_id) REFERENCES retry_policies(id) ON DELETE SET NULL,
    CONSTRAINT uk_project_queue UNIQUE (project_id, name)
);

-- Create jobs table
CREATE TABLE jobs (
    id BIGSERIAL PRIMARY KEY,
    queue_id BIGINT NOT NULL,
    parent_job_id BIGINT NULL,
    type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    payload TEXT NULL,
    priority INTEGER NOT NULL DEFAULT 0,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 3,
    worker_id VARCHAR(255) NULL,
    scheduled_at TIMESTAMP(3) NOT NULL,
    started_at TIMESTAMP(3) NULL,
    completed_at TIMESTAMP(3) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_jobs_queue FOREIGN KEY (queue_id) REFERENCES queues(id) ON DELETE RESTRICT,
    CONSTRAINT fk_jobs_parent FOREIGN KEY (parent_job_id) REFERENCES jobs(id) ON DELETE CASCADE
);

-- Create job_executions table
CREATE TABLE job_executions (
    id BIGSERIAL PRIMARY KEY,
    job_id BIGINT NOT NULL,
    worker_id VARCHAR(255) NOT NULL,
    attempt_number INTEGER NOT NULL,
    started_at TIMESTAMP(3) NOT NULL,
    ended_at TIMESTAMP(3) NULL,
    status VARCHAR(50) NOT NULL,
    error_message TEXT NULL,
    CONSTRAINT fk_exec_job FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE
);

-- Create workers table
CREATE TABLE workers (
    worker_id VARCHAR(255) PRIMARY KEY,
    hostname VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    last_heartbeat_at TIMESTAMP(3) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);

-- Create worker_heartbeats table
CREATE TABLE worker_heartbeats (
    id BIGSERIAL PRIMARY KEY,
    worker_id VARCHAR(255) NOT NULL,
    last_heartbeat_at TIMESTAMP(3) NOT NULL,
    status VARCHAR(50) NOT NULL,
    CONSTRAINT fk_hb_worker FOREIGN KEY (worker_id) REFERENCES workers(worker_id) ON DELETE CASCADE
);

-- Create job_logs table
CREATE TABLE job_logs (
    id BIGSERIAL PRIMARY KEY,
    job_id BIGINT NOT NULL,
    log_level VARCHAR(50) NOT NULL,
    message TEXT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_logs_job FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE
);

-- Create scheduled_jobs table
CREATE TABLE scheduled_jobs (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    queue_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    cron_expression VARCHAR(100) NOT NULL,
    job_payload TEXT NULL,
    priority INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    next_run_at TIMESTAMP(3) NULL,
    last_run_at TIMESTAMP(3) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_sched_proj FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE RESTRICT,
    CONSTRAINT fk_sched_queue FOREIGN KEY (queue_id) REFERENCES queues(id) ON DELETE RESTRICT,
    CONSTRAINT uk_proj_sched UNIQUE (project_id, name)
);

-- Create dead_letter_queue table
CREATE TABLE dead_letter_queue (
    id BIGSERIAL PRIMARY KEY,
    job_id BIGINT NOT NULL UNIQUE,
    final_error TEXT NULL,
    moved_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_dlq_job FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE
);

-- Create locks table for distributed locking
CREATE TABLE locks (
    lock_name VARCHAR(100) PRIMARY KEY,
    holder_id VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP(3) NOT NULL
);

-- Create mandatory indexes for optimizations
CREATE INDEX idx_jobs_queue_status_sched ON jobs(queue_id, status, scheduled_at);
CREATE INDEX idx_jobs_status_sched ON jobs(status, scheduled_at);
CREATE INDEX idx_worker_hb_wid_time ON worker_heartbeats(worker_id, last_heartbeat_at);
CREATE INDEX idx_jobs_parent ON jobs(parent_job_id);
