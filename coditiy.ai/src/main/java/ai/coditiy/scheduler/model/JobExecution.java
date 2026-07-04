package ai.coditiy.scheduler.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "job_executions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobExecution {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @Column(name = "worker_id", nullable = false, length = 255)
    private String workerId;

    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(nullable = false, length = 50)
    private String status; // COMPLETED, FAILED

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
}
