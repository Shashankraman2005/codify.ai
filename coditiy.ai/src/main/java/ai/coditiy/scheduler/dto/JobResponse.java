package ai.coditiy.scheduler.dto;

import ai.coditiy.scheduler.model.JobStatus;
import ai.coditiy.scheduler.model.JobType;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class JobResponse {
    private Long id;
    private Long queueId;
    private String queueName;
    private Long parentJobId;
    private JobType type;
    private JobStatus status;
    private String payload;
    private Integer priority;
    private Integer attemptCount;
    private Integer maxAttempts;
    private String workerId;
    private LocalDateTime scheduledAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Batch details (if applicable)
    private Long childJobCount;
    private Long completedChildCount;
    private Long failedChildCount;
}
