package ai.coditiy.scheduler.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "worker_heartbeats")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkerHeartbeat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "worker_id", nullable = false)
    private Worker worker;

    @Column(name = "last_heartbeat_at", nullable = false)
    private LocalDateTime lastHeartbeatAt;

    @Column(nullable = false, length = 50)
    private String status;
}
