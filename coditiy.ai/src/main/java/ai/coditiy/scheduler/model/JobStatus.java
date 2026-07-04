package ai.coditiy.scheduler.model;

public enum JobStatus {
    QUEUED, SCHEDULED, CLAIMED, RUNNING, COMPLETED, FAILED, DEAD_LETTER
}
