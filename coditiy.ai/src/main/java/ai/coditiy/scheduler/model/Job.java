package ai.coditiy.scheduler.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "jobs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Job {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "queue_id", nullable = false)
    private Queue queue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_job_id")
    private Job parentJob;

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.VARCHAR)
    @Column(nullable = false, length = 50)
    private JobType type;

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.VARCHAR)
    @Column(nullable = false, length = 50)
    private JobStatus status;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false)
    private Integer priority;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private Integer maxAttempts;

    @Column(name = "worker_id", length = 255)
    private String workerId;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (priority == null) priority = 0;
        if (attemptCount == null) attemptCount = 0;
        if (maxAttempts == null) maxAttempts = 3;
        if (status == null) status = JobStatus.QUEUED;
        if (scheduledAt == null) scheduledAt = LocalDateTime.now();
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
