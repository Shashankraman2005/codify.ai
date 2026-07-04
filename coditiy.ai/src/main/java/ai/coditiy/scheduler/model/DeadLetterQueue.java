package ai.coditiy.scheduler.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "dead_letter_queue")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeadLetterQueue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false, unique = true)
    private Job job;

    @Column(name = "final_error", columnDefinition = "TEXT")
    private String finalError;

    @Column(name = "moved_at", nullable = false)
    private LocalDateTime movedAt;

    @PrePersist
    protected void onCreate() {
        movedAt = LocalDateTime.now();
    }
}
