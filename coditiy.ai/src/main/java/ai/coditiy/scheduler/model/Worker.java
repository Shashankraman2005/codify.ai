package ai.coditiy.scheduler.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "workers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Worker {
    @Id
    @Column(name = "worker_id", length = 255)
    private String workerId;

    @Column(nullable = false, length = 255)
    private String hostname;

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.VARCHAR)
    @Column(nullable = false, length = 50)
    private WorkerStatus status;

    @Column(name = "last_heartbeat_at", nullable = false)
    private LocalDateTime lastHeartbeatAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        lastHeartbeatAt = LocalDateTime.now();
    }
}
