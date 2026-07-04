package ai.coditiy.scheduler.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "queues", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"project_id", "name"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Queue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false)
    private Integer priority;

    @Column(name = "concurrency_limit", nullable = false)
    private Integer concurrencyLimit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "retry_policy_id")
    private RetryPolicy retryPolicy;

    @Column(name = "is_paused", nullable = false)
    private Boolean isPaused;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (priority == null) priority = 0;
        if (concurrencyLimit == null) concurrencyLimit = 5;
        if (isPaused == null) isPaused = false;
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
