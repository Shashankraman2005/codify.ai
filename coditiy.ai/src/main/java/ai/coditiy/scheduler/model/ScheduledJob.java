package ai.coditiy.scheduler.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "scheduled_jobs", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"project_id", "name"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduledJob {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "queue_id", nullable = false)
    private Queue queue;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "cron_expression", nullable = false, length = 100)
    private String cronExpression;

    @Column(name = "job_payload", columnDefinition = "TEXT")
    private String jobPayload;

    @Column(nullable = false)
    private Integer priority;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "next_run_at")
    private LocalDateTime nextRunAt;

    @Column(name = "last_run_at")
    private LocalDateTime lastRunAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (priority == null) priority = 0;
        if (isActive == null) isActive = true;
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
