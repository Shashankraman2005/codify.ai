package ai.coditiy.scheduler.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "retry_policies")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RetryPolicy {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.VARCHAR)
    @Column(name = "strategy_type", nullable = false, length = 50)
    private RetryStrategy strategyType;

    @Column(name = "base_delay_seconds", nullable = false)
    private Integer baseDelaySeconds;

    @Column(name = "max_delay_seconds", nullable = false)
    private Integer maxDelaySeconds;

    @Column(name = "max_attempts", nullable = false)
    private Integer maxAttempts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
