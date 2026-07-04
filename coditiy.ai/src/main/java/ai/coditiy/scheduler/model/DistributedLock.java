package ai.coditiy.scheduler.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "locks")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DistributedLock {
    @Id
    @Column(name = "lock_name", length = 100)
    private String lockName;

    @Column(name = "holder_id", nullable = false, length = 255)
    private String holderId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}
