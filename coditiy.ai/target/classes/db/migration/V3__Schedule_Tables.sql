CREATE TABLE schedules (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    queue_id BIGINT NOT NULL REFERENCES queues(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    schedule_type VARCHAR(50) NOT NULL,
    cron_expression VARCHAR(120),
    interval_seconds INT,
    scheduled_at TIMESTAMP,
    payload TEXT,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_schedules_project ON schedules(project_id);
