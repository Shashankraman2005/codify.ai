package ai.coditiy.scheduler.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class QueueResponse {
    private Long id;
    private String name;
    private Long projectId;
    private String projectName;
    private Integer priority;
    private Integer concurrencyLimit;
    private Long retryPolicyId;
    private String retryPolicyName;
    private Boolean isPaused;
    private QueueStats stats;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
